package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.net.InetAddress

class CnameProtectionTest {
    private fun query()=Message.newQuery(Record.newRecord(Name("metrics.site.example."),Type.A,DClass.IN)).toWire()
    private fun response(raw:ByteArray,chain:List<String> = listOf("ads.example.")):ByteArray=Message(raw).apply {
        header.setFlag(Flags.QR.toInt())
        var owner=question.name
        for(name in chain){val target=Name(name);addRecord(CNAMERecord(owner,DClass.IN,60,target),Section.ANSWER);owner=target}
        addRecord(ARecord(owner,DClass.IN,60,InetAddress.getByName("192.0.2.1")),Section.ANSWER)
    }.toWire()
    @Test fun hiddenTrackerAndMultiHopAreBlocked(){
        val filter=FilterEngine().apply{replace(RuleSet(setOf("ads.example")))}
        for(chain in listOf(listOf("ads.example."),listOf("cdn.example.","nested.ads.example.")))for(tcp in listOf(false,true)) {
            val result=DnsEngine(filter,{response(it,chain)}).resolve(query(),tcp)
            assertEquals(Outcome.BLOCKED,result.outcome);assertEquals(Rcode.NXDOMAIN,Message(result.wire).rcode)
        }
    }
    @Test fun allowedFirstPartyAndAllowedTargetWin(){
        for(allow in listOf(setOf("metrics.site.example"),setOf("ads.example"))){
            val f=FilterEngine().apply{replace(RuleSet(setOf("ads.example"),allow=allow))}
            assertEquals(Outcome.ALLOWED,DnsEngine(f,{response(it)}).resolve(query()).outcome)
        }
    }
    @Test fun legitimateCdnAliasesRemainAllowed(){
        val f=FilterEngine().apply{replace(RuleSet(setOf("ads.example")))}
        assertEquals(Outcome.ALLOWED,DnsEngine(f,{response(it,listOf("cdn.example."))}).resolve(query()).outcome)
    }
    @Test fun cachedAliasesRecheckedAfterRuleChanges(){
        var calls=0;val f=FilterEngine();val e=DnsEngine(f,{calls++;response(it)})
        assertEquals(Outcome.ALLOWED,e.resolve(query()).outcome)
        f.replace(RuleSet(setOf("ads.example")))
        assertEquals(Outcome.BLOCKED,e.resolve(query()).outcome);assertEquals(1,calls)
        f.replace(RuleSet(setOf("ads.example"),allow=setOf("metrics.site.example")))
        assertEquals(Outcome.ALLOWED,e.resolve(query()).outcome);assertEquals(1,calls)
    }
    @Test fun unrelatedAnswerCannotCauseFalseBlock(){
        val f=FilterEngine().apply{replace(RuleSet(setOf("ads.example")))}
        val e=DnsEngine(f,{raw ->Message(response(raw,listOf("cdn.example."))).apply{addRecord(CNAMERecord(Name("unrelated.example."),DClass.IN,60,Name("ads.example.")),Section.ANSWER)}.toWire()})
        assertEquals(Outcome.ALLOWED,e.resolve(query()).outcome)
    }
    @Test fun pausedFilteringBypassesAliasProtection(){
        val f=FilterEngine().apply{replace(RuleSet(setOf("ads.example")))}
        assertEquals(Outcome.ALLOWED,DnsEngine(f,{response(it)}).resolve(query(),filtering=false).outcome)
    }
}
