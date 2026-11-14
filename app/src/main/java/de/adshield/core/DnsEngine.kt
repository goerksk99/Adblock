package de.adshield.core

import org.xbill.DNS.*
import org.xbill.DNS.Record

fun interface DnsUpstream { fun exchange(query: ByteArray): ByteArray }
enum class Outcome { ALLOWED, BLOCKED, ERROR }
data class DnsResult(val wire: ByteArray, val outcome: Outcome, val transportFailure: Boolean = false, val failureCause: Exception? = null)

/** Bounded DNS message cache. TTLs age using monotonic time, never wall-clock time. */
class DnsEngine(private val filter: FilterEngine, private val upstream: DnsUpstream, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Entry(val wire: ByteArray, val created: Long, val ttl: Long)
    private val cache = object : LinkedHashMap<String, Entry>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 512
    }
    private var cacheGeneration = 0L
    @Synchronized fun clearCache() { cacheGeneration++;cache.clear() }
    fun resolve(bytes: ByteArray, tcp: Boolean = false, filtering: Boolean = true): DnsResult {
        val query = try { Message(bytes) } catch (_: Exception) { return DnsResult(bareError(bytes), Outcome.ERROR) }
        val question = query.question
        if (query.header.getFlag(Flags.QR.toInt()) || query.header.getCount(Section.QUESTION) != 1 || question == null || query.tsig != null)
            return DnsResult(error(query, Rcode.FORMERR).toWire(), Outcome.ERROR)
        if (query.header.opcode != Opcode.QUERY || question.dClass != DClass.IN || question.type in setOf(Type.AXFR, Type.IXFR))
            return DnsResult(error(query, Rcode.REFUSED).toWire(), Outcome.ERROR)
        val udpSize = (query.opt?.payloadSize ?: 512).coerceIn(512, 1232)
        if (filtering && filter.blocked(question.name.toString())) {
            val response = error(query, Rcode.NXDOMAIN)
            response.addRecord(SOARecord(question.name, DClass.IN, 60, Name("blocked.invalid."), Name("hostmaster.blocked.invalid."), 1, 60, 60, 60, 60), Section.AUTHORITY)
            return DnsResult(response.toWire(if (tcp) 65535 else udpSize), Outcome.BLOCKED)
        }
        return try {
            val normalized = query.clone().apply { header.id = 0 }
            // Include every query flag and EDNS option in the key; do not mix DNSSEC/ECS requests.
            val key = java.util.Base64.getEncoder().encodeToString(normalized.toWire())
            val now = clock()
            val snapshot = synchronized(this) { cacheGeneration to cache[key]?.takeIf { now - it.created < it.ttl * 1000 } }
            val cached = snapshot.second
            val response: Message
            if (cached != null) {
                val age = ((now - cached.created) / 1000).coerceAtLeast(0)
                response = Message(cached.wire)
                for (section in Section.ANSWER..Section.ADDITIONAL) {
                    val records = response.getSection(section).toList()
                    response.removeAllRecords(section)
                    records.forEach { record ->
                        val aged = if (record is OPTRecord) record else record.rdataToWireCanonical().let { data ->
                            Record.newRecord(record.name, record.type, record.dClass, (record.ttl - age).coerceAtLeast(0), data.size, data)
                        }
                        response.addRecord(aged, section)
                    }
                }
            } else {
                val raw = upstream.exchange(bytes)
                require(raw.size in 12..65535) { "Ungültige DNS-Antwortgröße" }
                response = Message(raw)
                require(response.header.getFlag(Flags.QR.toInt()) && response.header.id == query.header.id && response.header.getCount(Section.QUESTION) == 1 && response.question == question && response.header.opcode == Opcode.QUERY) { "DNS-Antwort passt nicht zur Anfrage" }
                require(!response.header.getFlag(Flags.TC.toInt())) { "Unvollständige Upstream-Antwort" }
                if (response.rcode in setOf(Rcode.NOERROR, Rcode.NXDOMAIN) && response.tsig == null) {
                    val records = (Section.ANSWER..Section.ADDITIONAL).flatMap { response.getSection(it) }.filterNot { it is OPTRecord }
                    val negative = response.getSection(Section.AUTHORITY).filterIsInstance<SOARecord>().minOfOrNull { minOf(it.minimum, it.ttl) }
                    val ttl = if (response.rcode == Rcode.NXDOMAIN || response.getSection(Section.ANSWER).isEmpty()) minOf(negative ?: 0, records.minOfOrNull { it.ttl } ?: 0) else records.minOfOrNull { it.ttl } ?: 0
                    if (ttl > 0) synchronized(this) { if(cacheGeneration == snapshot.first)cache[key] = Entry(raw, now, ttl.coerceAtMost(300)) }
                }
            }
            response.header.id = query.header.id
            DnsResult(response.toWire(if (tcp) 65535 else udpSize), Outcome.ALLOWED)
        } catch (error: Exception) { DnsResult(error(query, Rcode.SERVFAIL).toWire(), Outcome.ERROR, true, error) }
    }
    private fun error(query: Message, code: Int) = Message(query.header.id).apply {
        header.setFlag(Flags.QR.toInt()); header.setFlag(Flags.RA.toInt())
        if (query.header.getFlag(Flags.RD.toInt())) header.setFlag(Flags.RD.toInt())
        header.rcode = code
        query.question?.let { addRecord(it, Section.QUESTION) }
    }
    private fun bareError(query: ByteArray): ByteArray = ByteArray(12).apply {
        if (query.size >= 2) { this[0] = query[0]; this[1] = query[1] }
        this[2] = 0x80.toByte(); this[3] = Rcode.FORMERR.toByte()
    }
}
