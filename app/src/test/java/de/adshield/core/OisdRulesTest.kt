package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.io.File

class OisdRulesTest {
    private val more by lazy{OisdRules.parse(File("src/main/assets/oisd-big.txt").readText())}
    private val base by lazy{HostsParser.parse(File("src/main/assets/hosts").readText())}
    private val maximum by lazy{MaximalRules.parse(File("src/main/assets/hagezi-ultimate.txt").readText())}
    @Test fun measuredAdditionalCoverageAndBoundaries(){
        val previous=MaximalRules.effective(base,maximum,true)
        assertEquals(228023,more.size)
        val additional=more.filterNot{Domains.matches(it,previous)}
        assertEquals(121559,additional.size)
        val current=MaximalRules.effective(base,maximum,true,more)
        println("OISD_COVERAGE previous=${previous.size} source=${more.size} newly_covered=${additional.size} effective=${current.size}")
        val rules=RuleSet(current)
        for(name in listOf("example.com","wikipedia.org","github.com","apple.com","tiktok.com"))assertFalse(name,rules.blocked(name))
        for(name in additional.take(1000)){
            assertTrue(name,rules.blocked(name));assertTrue(rules.blocked("sub.$name"))
            assertFalse(rules.blocked("$name.example.org"))
        }
    }
    @Test fun additionalDomainsNeverReachUpstreamAndAllowRulesWin(){
        val before=MaximalRules.effective(base,maximum,true)
        val additions=more.filterNot{Domains.matches(it,before)}.sorted().take(100)
        val current=MaximalRules.effective(base,maximum,true,more)
        val filter=FilterEngine().apply{replace(RuleSet(current))}
        val engine=DnsEngine(filter,{error("Newly blocked queries must stay local")})
        for(name in additions)for(type in listOf(Type.A,Type.AAAA,Type.HTTPS))for(tcp in listOf(false,true)) {
            val query=Message.newQuery(Record.newRecord(Name("$name."),type,DClass.IN)).toWire()
            assertEquals(name,Outcome.BLOCKED,engine.resolve(query,tcp).outcome)
        }
        assertFalse(RuleSet(current,allow=setOf(additions.first())).blocked("sub.${additions.first()}"))
        assertEquals(base,MaximalRules.effective(base,maximum,false,more))
    }
    @Test fun invalidListsFailClosedBeforeReplacingCurrentFiles(){
        val header="# Title: oisd big\n# Syntax: Domains (wildcards)\n# Entries: 2\n"
        for(text in listOf("<html>error</html>",header+"*.ads.example\n",header+"*.ads.example\n*.ads.example\n",header+"*.ads.example\n||tracker.example^\n")) assertTrue(runCatching{OisdRules.parse(text,1)}.isFailure)
        assertEquals(setOf("ads.example","tracker.example"),OisdRules.parse(header+"*.ads.example\n*.tracker.example\n",1))
    }
}
