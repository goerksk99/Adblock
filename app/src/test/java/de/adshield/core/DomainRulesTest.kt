package de.adshield.core

import org.junit.Assert.*
import org.junit.Test

class DomainRulesTest {
    @Test fun knownBlocked(){assertTrue(RuleSet(setOf("ads.example")).blocked("ads.example"))}
    @Test fun normalAllowed(){assertFalse(RuleSet(setOf("ads.example")).blocked("example.org"))}
    @Test fun allowOverridesAllBlocks(){assertFalse(RuleSet(setOf("example.com"),setOf("safe.example.com"),setOf("safe.example.com")).blocked("a.safe.example.com"))}
    @Test fun customDeny(){assertTrue(RuleSet(deny=setOf("custom.example")).blocked("a.custom.example"))}
    @Test fun labelBoundaries(){val r=RuleSet(setOf("ads.example"));assertFalse(r.blocked("badads.example"));assertFalse(r.blocked("ads.example.evil"));assertTrue(r.blocked("a.ads.example"))}
    @Test fun caseAndTrailingDot(){assertEquals("example.com",Domains.normalize(" ExAmPlE.COM. "))}
    @Test fun idn(){assertEquals("xn--bcher-kva.de",Domains.normalize("bücher.de"))}
    @Test fun invalidDomains(){listOf("", "http://example.com", "a..com", "-test.com", "*.example.com", "127.0.0.1","a@b.com","a/b.com").forEach{assertTrue(it,runCatching{Domains.normalize(it)}.isFailure)}}
    @Test fun atomicSnapshots(){val f=FilterEngine();f.replace(RuleSet(setOf("ads.example")));assertTrue(f.blocked("ads.example"));f.replace(RuleSet(allow=setOf("ads.example")));assertFalse(f.blocked("ads.example"))}
    @Test fun hostsValidDeduplicated(){val t="# StevenBlack/hosts\n0.0.0.0 ads.example\n0.0.0.0 ADS.example\n127.0.0.1 tracker.example";assertEquals(setOf("ads.example","tracker.example"),HostsParser.parse(t,2))}
    @Test fun emptyAndHtmlRejected(){listOf("", "<html>error</html>","# StevenBlack/hosts\n").forEach{assertTrue(runCatching{HostsParser.parse(it)}.isFailure)}}
    @Test fun truncatedListRejected(){assertTrue(runCatching{HostsParser.parse("# StevenBlack/hosts\n0.0.0.0 ads.example")}.isFailure)}
    @Test fun bundledMatchingBenchmark(){
        val text=java.io.File("src/main/assets/hosts").readText()
        val parseStart=System.nanoTime();val hosts=HostsParser.parse(text)
        val parseMs=(System.nanoTime()-parseStart)/1_000_000.0
        val rules=RuleSet(hosts);var hits=0;val start=System.nanoTime()
        repeat(100000){i ->if(rules.blocked(listOf("doubleclick.net","a.doubleclick.net","example.com","login.example.com")[i%4]))hits++}
        val ms=(System.nanoTime()-start)/1_000_000.0
        assertEquals(50000,hits)
        println("HOST_BENCHMARK domains=${hosts.size} parse_ms=$parseMs queries=100000 elapsed_ms=$ms average_us=${ms/100}")
    }
    @Test fun declaredCountDetectsOtherwisePlausiblePartialDownload(){
        val partial="# StevenBlack/hosts\n# Number of unique domains: 2,000\n"+(0 until 1100).joinToString("\n"){"0.0.0.0 ads-$it.example"}
        assertTrue(runCatching{HostsParser.parse(partial)}.isFailure)
    }
}
