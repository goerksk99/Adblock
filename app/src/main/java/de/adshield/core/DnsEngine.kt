package de.adshield.core

import org.xbill.DNS.*
import org.xbill.DNS.Record

fun interface DnsUpstream { fun exchange(query: ByteArray): ByteArray }
enum class Outcome { ALLOWED, BLOCKED, ERROR }
data class DnsResult(val wire: ByteArray, val outcome: Outcome, val transportFailure: Boolean = false, val failureCause: Exception? = null)

/** Bounded DNS message cache. TTLs age using monotonic time, never wall-clock time. */
class DnsEngine(private val filter: FilterEngine, private val upstream: DnsUpstream, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }, private val cacheBudgetBytes:Int=4*1024*1024) {
    private data class Entry(val wire: ByteArray, val created: Long, val ttl: Long)
    private val cache = LinkedHashMap<String,Entry>(256,.75f,true)
    private var cacheBytes=0
    private fun cost(key:String,entry:Entry)=key.length*2+entry.wire.size+128
    @Synchronized private fun remember(key:String,entry:Entry,generation:Long){
        if(generation!=cacheGeneration || cost(key,entry)>cacheBudgetBytes)return
        cache.remove(key)?.let{cacheBytes-=cost(key,it)}
        cache[key]=entry;cacheBytes+=cost(key,entry)
        while(cache.size>512 || cacheBytes>cacheBudgetBytes){val first=cache.entries.iterator().next();cacheBytes-=cost(first.key,first.value);cache.remove(first.key)}
    }
    private var cacheGeneration = 0L
    @Synchronized fun clearCache() { cacheGeneration++;cache.clear();cacheBytes=0 }
    fun resolve(bytes: ByteArray, tcp: Boolean = false, filtering: Boolean = true): DnsResult {
        val query = try { Message(bytes) } catch (_: Exception) { return DnsResult(bareError(bytes), Outcome.ERROR) }
        val question = query.question
        if (query.header.getFlag(Flags.QR.toInt()) || query.header.getCount(Section.QUESTION) != 1 || question == null || query.tsig != null)
            return DnsResult(error(query, Rcode.FORMERR).toWire(), Outcome.ERROR)
        if (query.header.opcode != Opcode.QUERY || question.dClass != DClass.IN || question.type in setOf(Type.AXFR, Type.IXFR))
            return DnsResult(error(query, Rcode.REFUSED).toWire(), Outcome.ERROR)
        val udpSize = (query.opt?.payloadSize ?: 512).coerceIn(512, 1232)
        if (filtering && filter.blocked(question.name.toString())) {
            return blocked(query,tcp,udpSize)
        }
        return try {
            val normalized = query.clone().apply { header.id = 0 }
            // Include every query flag and EDNS option in the key; do not mix DNSSEC/ECS requests.
            val key = java.util.Base64.getEncoder().encodeToString(normalized.toWire())
            val now = clock()
            val snapshot = synchronized(this) { cacheGeneration to cache[key]?.takeIf { now - it.created < it.ttl * 1000 } }
            val cached = snapshot.second
            val response: Message
            var pendingEntry:Entry?=null
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
                val raw = try{upstream.exchange(bytes)}catch(e:Exception){
                    return DnsResult(error(query,Rcode.SERVFAIL).toWire(),Outcome.ERROR,true,e)
                }
                require(raw.size in 12..65535) { "Ungültige DNS-Antwortgröße" }
                response = Message(raw)
                require(response.header.getFlag(Flags.QR.toInt()) && response.header.id == query.header.id && response.header.getCount(Section.QUESTION) == 1 && response.question == question && response.header.opcode == Opcode.QUERY) { "DNS-Antwort passt nicht zur Anfrage" }
                require(!response.header.getFlag(Flags.TC.toInt())) { "Unvollständige Upstream-Antwort" }
                if (response.rcode in setOf(Rcode.NOERROR, Rcode.NXDOMAIN) && response.tsig == null) {
                    val records = (Section.ANSWER..Section.ADDITIONAL).flatMap { response.getSection(it) }.filterNot { it is OPTRecord }
                    val negative = response.getSection(Section.AUTHORITY).filterIsInstance<SOARecord>().minOfOrNull { minOf(it.minimum, it.ttl) }
                    val ttl = if (response.rcode == Rcode.NXDOMAIN || response.getSection(Section.ANSWER).isEmpty()) minOf(negative ?: 0, records.minOfOrNull { it.ttl } ?: 0) else records.minOfOrNull { it.ttl } ?: 0
                    if (ttl > 0 && bytes.size<=4096)pendingEntry=Entry(raw,clock(),ttl.coerceAtMost(300))
                }
            }
            // Recheck raw/cached aliases against current rules on every request.
            // Only follow the chain rooted in this question; unrelated answer names cannot block it.
            val blockedAlias=AliasPolicy.apply(filter,question,response,filtering)
            pendingEntry?.let{remember(key,it,snapshot.first)}
            if(blockedAlias)return blocked(query,tcp,udpSize)
            response.header.id = query.header.id
            DnsResult(response.toWire(if (tcp) 65535 else udpSize), if(response.rcode in setOf(Rcode.NOERROR,Rcode.NXDOMAIN))Outcome.ALLOWED else Outcome.ERROR)
        } catch (error: Exception) { DnsResult(error(query, Rcode.SERVFAIL).toWire(), Outcome.ERROR, false, error) }
    }
    fun unavailable(bytes:ByteArray):DnsResult = DnsResponses.unavailable(bytes)
    private fun blocked(query:Message,tcp:Boolean,udpSize:Int):DnsResult {
        val response=error(query,Rcode.NXDOMAIN)
        response.addRecord(SOARecord(query.question.name,DClass.IN,60,Name("blocked.invalid."),Name("hostmaster.blocked.invalid."),1,60,60,60,60),Section.AUTHORITY)
        return DnsResult(response.toWire(if(tcp)65535 else udpSize),Outcome.BLOCKED)
    }
    private fun error(query:Message,code:Int)=DnsResponses.error(query,code)
    private fun bareError(query:ByteArray)=DnsResponses.bare(query)
}
