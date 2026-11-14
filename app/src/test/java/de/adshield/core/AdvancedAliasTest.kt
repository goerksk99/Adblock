package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.net.InetAddress
import java.time.Instant

class AdvancedAliasTest {
    private val origin=Name("a.site.example.")
    private fun resolve(type:Int=Type.A,allowed:Set<String> = emptySet(),records:(Message)->Unit):DnsResult {
        val f=FilterEngine().apply{replace(RuleSet(setOf("tracker.example"),allow=allowed))}
        val engine=DnsEngine(f,{raw ->Message(raw).apply{header.setFlag(Flags.QR.toInt());records(this)}.toWire()})
        return engine.resolve(Message.newQuery(Record.newRecord(origin,type,DClass.IN)).toWire())
    }
    @Test fun dnameRewriteAndLongestOwner(){
        val r=resolve{m ->m.addRecord(DNAMERecord(Name("site.example."),DClass.IN,60,Name("tracker.example.")),Section.ANSWER)}
        assertEquals(Outcome.BLOCKED,r.outcome)
    }
    @Test fun closestDnameAndUnrelatedRecordPreserveNormalSite(){
        val r=resolve{m ->
            m.addRecord(DNAMERecord(Name("example."),DClass.IN,60,Name("tracker.example.")),Section.ANSWER)
            m.addRecord(DNAMERecord(Name("site.example."),DClass.IN,60,Name("cdn.safe.test.")),Section.ANSWER)
        }
        assertEquals(Outcome.ALLOWED,r.outcome)
    }
    @Test fun httpsAndSvcbAliasModesBlockKnownTargets(){
        for(type in listOf(Type.HTTPS,Type.SVCB)){
            val r=resolve(type){m ->m.addRecord(if(type==Type.HTTPS)HTTPSRecord(origin,DClass.IN,60,0,Name("tracker.example."),emptyList()) else SVCBRecord(origin,DClass.IN,60,0,Name("tracker.example."),emptyList()),Section.ANSWER)}
            assertEquals(Outcome.BLOCKED,r.outcome)
        }
    }
    @Test fun serviceAlternativesAreFilteredWithoutBlockingContentOrigin(){
        val r=resolve(Type.HTTPS){m ->
            m.header.setFlag(Flags.AD.toInt())
            m.addRecord(HTTPSRecord(origin,DClass.IN,60,1,Name("tracker.example."),emptyList()),Section.ANSWER)
            m.addRecord(HTTPSRecord(origin,DClass.IN,60,2,Name("cdn.safe.example."),emptyList()),Section.ANSWER)
            m.addRecord(RRSIGRecord(origin,DClass.IN,60,Type.HTTPS,8,60,Instant.now().plusSeconds(60),Instant.now().minusSeconds(60),123,Name("signer.example."),byteArrayOf(1)),Section.ANSWER)
            m.addRecord(ARecord(Name("tracker.example."),DClass.IN,60,InetAddress.getByName("192.0.2.1")),Section.ADDITIONAL)
        }
        val m=Message(r.wire)
        assertEquals(Outcome.ALLOWED,r.outcome);assertEquals(Rcode.NOERROR,m.rcode)
        assertEquals(listOf(Name("cdn.safe.example.")),m.getSection(Section.ANSWER).filterIsInstance<HTTPSRecord>().map{it.targetName})
        assertFalse(m.header.getFlag(Flags.AD.toInt()));assertTrue(m.getSection(Section.ANSWER).none{it is RRSIGRecord})
        assertTrue(m.getSection(Section.ADDITIONAL).none{it.name==Name("tracker.example.")})
    }
    @Test fun serviceRootAndExplicitAllowArePreserved(){
        val root=resolve(Type.HTTPS){it.addRecord(HTTPSRecord(origin,DClass.IN,60,1,Name.root,emptyList()),Section.ANSWER)}
        assertEquals(1,Message(root.wire).getSection(Section.ANSWER).size)
        val allow=resolve(Type.HTTPS,setOf("site.example")){it.addRecord(HTTPSRecord(origin,DClass.IN,60,0,Name("tracker.example."),emptyList()),Section.ANSWER)}
        assertEquals(Outcome.ALLOWED,allow.outcome)
    }
    @Test fun loopConflictingAliasesAndOverlongChainsFailOnlyThisQuery(){
        val variants=listOf<(Message)->Unit>(
            {m ->m.addRecord(CNAMERecord(origin,DClass.IN,60,origin),Section.ANSWER)},
            {m ->m.addRecord(CNAMERecord(origin,DClass.IN,60,Name("one.example.")),Section.ANSWER);m.addRecord(CNAMERecord(origin,DClass.IN,60,Name("two.example.")),Section.ANSWER)},
            {m ->var n=origin;repeat(35){i ->val next=Name("hop-$i.example.");m.addRecord(CNAMERecord(n,DClass.IN,60,next),Section.ANSWER);n=next}}
        )
        for(v in variants){val result=resolve(records=v);assertEquals(Outcome.ERROR,result.outcome);assertFalse(result.transportFailure)}
    }
    @Test fun unrelatedAndWrongClassBindingsCannotBlockThisQuestion(){
        val r=resolve(Type.HTTPS){m ->m.addRecord(HTTPSRecord(Name("other.example."),DClass.IN,60,0,Name("tracker.example."),emptyList()),Section.ANSWER)}
        assertEquals(Outcome.ALLOWED,r.outcome)
    }
    @Test fun aliasRootMeansUnavailableServiceNotBrokenDns(){
        val r=resolve(Type.HTTPS){it.addRecord(HTTPSRecord(origin,DClass.IN,60,0,Name.root,emptyList()),Section.ANSWER)}
        assertEquals(Outcome.ALLOWED,r.outcome);assertEquals(Rcode.NOERROR,Message(r.wire).rcode)
    }
    @Test fun invalidAliasesAreNeverCached(){
        var calls=0;val engine=DnsEngine(FilterEngine(),{raw ->Message(raw).apply{
            header.setFlag(Flags.QR.toInt())
            if(calls++==0)addRecord(CNAMERecord(question.name,DClass.IN,60,question.name),Section.ANSWER)
            else addRecord(ARecord(question.name,DClass.IN,60,InetAddress.getByName("192.0.2.1")),Section.ANSWER)
        }.toWire()})
        val q=Message.newQuery(Record.newRecord(origin,Type.A,DClass.IN)).toWire()
        assertEquals(Outcome.ERROR,engine.resolve(q).outcome)
        assertEquals(Outcome.ALLOWED,engine.resolve(q).outcome);assertEquals(2,calls)
    }
    @Test fun dnameDoesNotRenameItsExactOwner(){
        assertEquals(Outcome.ALLOWED,resolve{it.addRecord(DNAMERecord(origin,DClass.IN,60,Name("tracker.example.")),Section.ANSWER)}.outcome)
    }
    @Test fun serviceBindingsForAnotherQueryTypeAreNotInterpretedAsAliases(){
        assertEquals(Outcome.ALLOWED,resolve(Type.A){it.addRecord(HTTPSRecord(origin,DClass.IN,60,0,Name("tracker.example."),emptyList()),Section.ANSWER)}.outcome)
    }
    @Test fun cachedServiceAlternativesRestoreAfterAnAllowChange(){
        val f=FilterEngine().apply{replace(RuleSet(setOf("tracker.example")))};var calls=0
        val e=DnsEngine(f,{raw ->calls++;Message(raw).apply{header.setFlag(Flags.QR.toInt());addRecord(HTTPSRecord(origin,DClass.IN,60,1,Name("tracker.example."),emptyList()),Section.ANSWER)}.toWire()})
        val q=Message.newQuery(Record.newRecord(origin,Type.HTTPS,DClass.IN)).toWire()
        assertTrue(Message(e.resolve(q).wire).getSection(Section.ANSWER).isEmpty())
        f.replace(RuleSet(setOf("tracker.example"),allow=setOf("tracker.example")))
        assertEquals(1,Message(e.resolve(q).wire).getSection(Section.ANSWER).size);assertEquals(1,calls)
    }
}
