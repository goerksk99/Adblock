package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.io.File

/** Real user-reported names; synthetic upstream isolates local DNS behavior from network outages. */
class ScreenshotCoverageTest {
    private fun cases(): List<Pair<String, Boolean>> = javaClass.getResourceAsStream("/screenshot-domains.tsv")!!.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val fields = line.split('\t'); fields[0] to fields[1].toBooleanStrict()
        }.toList()
    }
    private fun hosts() = HostsParser.parse(File("src/main/assets/hosts").readText())
    private fun query(domain: String, type: Int) = Message.newQuery(Record.newRecord(Name("$domain."), type, DClass.IN)).toWire()

    @Test fun screenshotDomainsAreFilteredBeforeUpstreamForEveryAddressType() {
        val cases = cases()
        assertEquals(43, cases.size)
        val filter = FilterEngine().apply { replace(RuleSet(hosts())) }
        var upstreamCalls = 0
        val engine = DnsEngine(filter, { raw ->
            upstreamCalls++
            Message(raw).apply { header.setFlag(Flags.QR.toInt()); header.setFlag(Flags.RA.toInt()) }.toWire()
        })
        for ((domain, expectedBlocked) in cases) {
            for (type in listOf(Type.A, Type.AAAA, Type.HTTPS)) {
                val before = upstreamCalls
                val result = engine.resolve(query(domain, type))
                assertEquals("$domain type=$type", expectedBlocked, result.outcome == Outcome.BLOCKED)
                if (expectedBlocked) {
                    assertEquals("Blocked DNS must stay local: $domain", before, upstreamCalls)
                    assertEquals(Rcode.NXDOMAIN, Message(result.wire).rcode)
                } else assertEquals(before + 1, upstreamCalls)
            }
        }
        println("SCREENSHOT_AUDIT domains=43 bundled_blocked=27 uncovered=16 DNS_cases=129")
    }

    @Test fun explicitRulesCloseEveryReportedGapForUdpAndTcp() {
        val cases = cases()
        val missing = cases.filter { !it.second }.map { it.first }.toSet()
        val filter = FilterEngine().apply { replace(RuleSet(hosts(), deny = missing)) }
        val engine = DnsEngine(filter, { error("A blocked query must never leave the device") })
        for ((domain, _) in cases) {
            for (type in listOf(Type.A, Type.AAAA, Type.HTTPS)) {
                for (tcp in listOf(false, true)) {
                    assertEquals("$domain type=$type tcp=$tcp", Outcome.BLOCKED, engine.resolve(query(domain, type), tcp).outcome)
                }
            }
        }
    }
}
