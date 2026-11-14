package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.net.InetAddress
import java.net.SocketTimeoutException

class DnsEngineTest {
    private fun query(type:Int=Type.A,name:String="example.com.")=Message.newQuery(Record.newRecord(Name(name),type,DClass.IN))
    private fun answer(raw:ByteArray,ttl:Long=60):ByteArray=Message(raw).apply{
        header.setFlag(Flags.QR.toInt());header.setFlag(Flags.RA.toInt())
        addRecord(if(question.type==Type.AAAA)AAAARecord(question.name,DClass.IN,ttl,InetAddress.getByName("2001:db8::1"))else ARecord(question.name,DClass.IN,ttl,InetAddress.getByName("192.0.2.1")),Section.ANSWER)
    }.toWire()
    @Test fun aRecord(){val q=query();val r=DnsEngine(FilterEngine(),{answer(it)}).resolve(q.toWire());assertEquals(Outcome.ALLOWED,r.outcome);assertTrue(Message(r.wire).getSection(Section.ANSWER).first() is ARecord)}
    @Test fun aaaaRecord(){val r=DnsEngine(FilterEngine(),{answer(it)}).resolve(query(Type.AAAA).toWire());assertTrue(Message(r.wire).getSection(Section.ANSWER).first() is AAAARecord)}
    @Test fun blockedNxdomainWithNegativeTtl(){val f=FilterEngine().apply{replace(RuleSet(setOf("ads.example")))};val r=DnsEngine(f,{error("Should never reach upstream")}).resolve(query(name="ads.example.").toWire());assertEquals(Outcome.BLOCKED,r.outcome);val m=Message(r.wire);assertEquals(Rcode.NXDOMAIN,m.rcode);assertFalse(m.header.getFlag(Flags.AD.toInt()));assertEquals(60L,(m.getSection(Section.AUTHORITY).first() as SOARecord).minimum)}
    @Test fun timeoutBecomesServfail(){val r=DnsEngine(FilterEngine(),{throw SocketTimeoutException()}).resolve(query().toWire());assertEquals(Outcome.ERROR,r.outcome);assertEquals(Rcode.SERVFAIL,Message(r.wire).rcode)}
    @Test fun malformedQuery(){val r=DnsEngine(FilterEngine(),{error("not called")}).resolve(byteArrayOf(0x12,0x34));assertEquals(0x1234,Message(r.wire).header.id);assertEquals(Rcode.FORMERR,Message(r.wire).rcode)}
    @Test fun responseIdMismatchRejected(){val r=DnsEngine(FilterEngine(),{answer(it).also{b ->b[0]=(b[0].toInt() xor 255).toByte()}}).resolve(query().toWire());assertEquals(Outcome.ERROR,r.outcome)}
    @Test fun responseQuestionMismatchRejected(){val r=DnsEngine(FilterEngine(),{answer(query(name="other.example.").toWire())}).resolve(query().toWire());assertEquals(Outcome.ERROR,r.outcome)}
    @Test fun cacheAgesTtlAndExpires(){var now=0L;var calls=0;val e=DnsEngine(FilterEngine(),{calls++;answer(it,10)},{now});val q=query();e.resolve(q.toWire());now=4000;val m=Message(e.resolve(q.toWire()).wire);assertEquals(6L,m.getSection(Section.ANSWER).first().ttl);assertEquals(1,calls);now=10000;e.resolve(q.toWire());assertEquals(2,calls)}
    @Test fun cacheRestoresTransactionId(){var calls=0;val e=DnsEngine(FilterEngine(),{calls++;answer(it)});val q=query();q.header.id=10;e.resolve(q.toWire());q.header.id=20;assertEquals(20,Message(e.resolve(q.toWire()).wire).header.id);assertEquals(1,calls)}
    @Test fun newBlockRuleOverridesWarmCache(){val f=FilterEngine();val e=DnsEngine(f,{answer(it)});val q=query();e.resolve(q.toWire());f.replace(RuleSet(setOf("example.com")));assertEquals(Outcome.BLOCKED,e.resolve(q.toWire()).outcome)}
    @Test fun udpTruncatesTcpPreserves(){val upstream=DnsUpstream{raw ->Message(raw).apply{header.setFlag(Flags.QR.toInt());repeat(80){addRecord(TXTRecord(question.name,DClass.IN,60,"record-$it-"+"x".repeat(100)),Section.ANSWER)}}.toWire()};val e=DnsEngine(FilterEngine(),upstream);val q=query(Type.TXT).toWire();val udp=e.resolve(q).wire;assertTrue(udp.size<=512);assertTrue(Message(udp).header.getFlag(Flags.TC.toInt()));val tcp=e.resolve(q,true).wire;assertFalse(Message(tcp).header.getFlag(Flags.TC.toInt()));assertEquals(80,Message(tcp).getSection(Section.ANSWER).size)}
    @Test fun upstreamRcodePreserved(){val e=DnsEngine(FilterEngine(),{raw -> Message(raw).apply{header.setFlag(Flags.QR.toInt());header.rcode=Rcode.SERVFAIL}.toWire()});assertEquals(Rcode.SERVFAIL,Message(e.resolve(query().toWire()).wire).rcode)}
    @Test fun passThroughSkipsLocalRules(){val f=FilterEngine().apply{replace(RuleSet(setOf("example.com")))};assertEquals(Outcome.ALLOWED,DnsEngine(f,{answer(it)}).resolve(query().toWire(),filtering=false).outcome)}
    @Test fun nonQueryRefused(){val q=query().apply{header.opcode=Opcode.UPDATE};assertEquals(Rcode.REFUSED,Message(DnsEngine(FilterEngine(),{error("not called")}).resolve(q.toWire()).wire).rcode)}
    @Test fun ednsLimitIsRespected(){
        val q=query(Type.TXT).apply{addRecord(OPTRecord(4096,0,0),Section.ADDITIONAL)}
        val e=DnsEngine(FilterEngine(),{raw ->Message(raw).apply{header.setFlag(Flags.QR.toInt());repeat(50){addRecord(TXTRecord(question.name,DClass.IN,60,"$it-"+"x".repeat(90)),Section.ANSWER)}}.toWire()})
        val response=e.resolve(q.toWire()).wire;assertTrue(response.size<=1232);assertTrue(Message(response).header.getFlag(Flags.TC.toInt()))
    }
    @Test fun negativeCacheUsesSoaMinimum(){
        var now=0L;var calls=0
        val e=DnsEngine(FilterEngine(),{raw ->calls++;Message(raw).apply{
            header.setFlag(Flags.QR.toInt());header.rcode=Rcode.NXDOMAIN
            addRecord(SOARecord(Name("example.com."),DClass.IN,120,Name("ns.example.com."),Name("admin.example.com."),1,10,10,10,20),Section.AUTHORITY)
        }.toWire()},{now})
        val q=query().toWire();e.resolve(q);now=19000;e.resolve(q);assertEquals(1,calls);now=21000;e.resolve(q);assertEquals(2,calls)
    }
    @Test fun invalidWireInputCannotCrashEngine(){val random=java.util.Random(83);val e=DnsEngine(FilterEngine(),{throw SocketTimeoutException()});repeat(1000){val bytes=ByteArray(random.nextInt(256));random.nextBytes(bytes);e.resolve(bytes)}}
    @Test fun cacheClearDuringInflightResponseDoesNotRepopulateStaleCache(){
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val e=DnsEngine(FilterEngine(),{raw ->
            if(calls.incrementAndGet()==1){entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
            answer(raw)
        })
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val first=executor.submit<DnsResult>{e.resolve(query().toWire())}
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));e.clearCache();release.countDown()
            assertEquals(Outcome.ALLOWED,first.get(5,java.util.concurrent.TimeUnit.SECONDS).outcome)
            e.resolve(query().toWire());assertEquals(2,calls.get())
        }finally{release.countDown();executor.shutdownNow()}
    }
    @Test fun dnsServfailIsAQueryErrorWithoutPretendingTransportFailed(){
        val e=DnsEngine(FilterEngine(),{raw ->Message(raw).apply{header.setFlag(Flags.QR.toInt());header.rcode=Rcode.SERVFAIL}.toWire()})
        val result=e.resolve(query().toWire());assertEquals(Outcome.ERROR,result.outcome);assertFalse(result.transportFailure)
    }
}
