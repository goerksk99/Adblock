package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WorkQueueTest {
    @Test fun boundedAdmissionAndExpiredTasksDoNotRunUpstream(){
        var now=0L;val entered=CountDownLatch(1);val release=CountDownLatch(1);val expired=CountDownLatch(2);val ran=AtomicInteger()
        val q=DnsWorkQueue(workers=1,capacity=1,maxWaitMillis=100,clock={now})
        try{
            q.submit({entered.countDown();release.await(5,TimeUnit.SECONDS)},{fail("First task was rejected")})
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            q.submit({ran.incrementAndGet()},{expired.countDown()})
            q.submit({ran.incrementAndGet()},{expired.countDown()})
            now=200;release.countDown();assertTrue(expired.await(5,TimeUnit.SECONDS));assertEquals(0,ran.get())
        }finally{release.countDown();q.shutdownNow()}
    }
    @Test fun shutdownRejectsWithoutSilentlyLosingTheRequest(){
        val q=DnsWorkQueue();q.shutdownNow();var rejected=false
        q.submit({fail("Closed queue")},{rejected=true});assertTrue(rejected)
    }
    @Test fun overloadReplyIsLocalServfailNotATransportFailure(){
        val e=DnsEngine(FilterEngine(),{error("Must not use upstream")})
        val query=org.xbill.DNS.Message.newQuery(org.xbill.DNS.Record.newRecord(org.xbill.DNS.Name("example.com."),org.xbill.DNS.Type.A,org.xbill.DNS.DClass.IN))
        val r=e.unavailable(query.toWire());assertEquals(org.xbill.DNS.Rcode.SERVFAIL,org.xbill.DNS.Message(r.wire).rcode);assertFalse(r.transportFailure)
    }
    @Test fun rejectedTcpPacketsHaveValidResetsWithoutResetLoops(){
        for(version in listOf(4,6))for(flags in listOf(2,16,4)){
            val source=java.net.InetAddress.getByName(if(version==4)"10.111.0.1" else "fdad:51e1:d::1").address
            val target=java.net.InetAddress.getByName(if(version==4)"10.111.0.2" else "fdad:51e1:d::2").address
            val tcp=ByteArray(20).apply{Wire.put16(this,0,40000);Wire.put16(this,2,53);Wire.put32(this,4,42);Wire.put32(this,8,900);this[12]=0x50;this[13]=flags.toByte()}
            val p=checkNotNull(IpPacket.parse(IpPacket.encode(version,source,target,6,tcp)))
            if(flags==4)assertNull(p.tcpReset())
            else {
                val reset=checkNotNull(IpPacket.parse(checkNotNull(p.tcpReset())))
                assertArrayEquals(target,reset.source);assertArrayEquals(source,reset.destination)
                assertEquals(40000,reset.destinationPort)
                if(flags==2){assertEquals(20,reset.transport[13].toInt());assertEquals(43L,Wire.u32(reset.transport,8))}
                else {assertEquals(4,reset.transport[13].toInt());assertEquals(900L,Wire.u32(reset.transport,4))}
            }
        }
    }
}
