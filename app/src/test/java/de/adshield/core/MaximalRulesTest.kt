package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.io.File

class MaximalRulesTest {
    private val maximum by lazy { MaximalRules.parse(File("src/main/assets/hagezi-ultimate.txt").readText()) }
    private val base by lazy { HostsParser.parse(File("src/main/assets/hosts").readText()) }
    @Test fun strongerListIsValidAndDeduplicated() {
        assertEquals(272267,maximum.size)
        val combined=MaximalRules.effective(base,maximum,true)
        assertTrue(combined.size>base.size)
        assertEquals(base,MaximalRules.effective(base,maximum,false))
        println("MAXIMUM_FILTER base=${base.size} ultimate=${maximum.size} effective=${combined.size}")
    }
    @Test fun reportedNamesBlockLocallyForAllDnsTypesAndTransports() {
        val rules=MaximalRules.effective(base,maximum,true)
        val filter=FilterEngine().apply { replace(RuleSet(rules)) }
        val engine=DnsEngine(filter,{error("Blocked queries must not leave the device")})
        val cases=javaClass.getResourceAsStream("/screenshot-domains.tsv")!!.bufferedReader().useLines { it.filter{line->line.isNotBlank()&&!line.startsWith("#")}.map{line->line.substringBefore('\t')}.toList() }
        for(name in cases) for(type in listOf(Type.A,Type.AAAA,Type.HTTPS)) for(tcp in listOf(false,true)) {
            val query=Message.newQuery(Record.newRecord(Name("$name."),type,DClass.IN)).toWire()
            val result=engine.resolve(query,tcp)
            assertEquals("$name $type TCP=$tcp",Outcome.BLOCKED,result.outcome)
            assertEquals(Rcode.NXDOMAIN,Message(result.wire).rcode)
        }
    }
    @Test fun allowRulesAndLabelBoundariesPreserveNormalContent() {
        val combined=MaximalRules.effective(base,maximum,true)
        val rules=RuleSet(combined,allow=setOf("freshmarketer.com"))
        assertFalse(rules.blocked("claritybt.freshmarketer.com"))
        for(name in listOf("example.com","wikipedia.org","github.com","tiktok.com","yahoo.com","apple.com","freshmarketer.com.example.org")) assertFalse(name,rules.blocked(name))
        assertTrue(rules.blocked("ads-api.tiktok.com"))
    }
    @Test fun malformedTruncatedHtmlAndDuplicatesRejected() {
        val header="# Title: HaGeZi's Multi ULTIMATE\n# Syntax: Domains\n# Number of entries: 2\n"
        for(text in listOf("<html>Error</html>",header+"ads.example\n",header+"ads.example\nads.example\n",header+"ads.example\n*.example.org\n")) {
            assertTrue(runCatching{MaximalRules.parse(text,1)}.isFailure)
        }
        assertEquals(setOf("ads.example","tracker.example"),MaximalRules.parse(header+"ads.example\ntracker.example\n",1))
    }
}
