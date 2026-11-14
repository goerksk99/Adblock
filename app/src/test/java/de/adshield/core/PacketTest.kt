package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class PacketTest {
    private fun ip(version:Int,proto:Int,segment:ByteArray)=IpPacket.encode(version,InetAddress.getByName(if(version==4)"10.111.0.1" else "fdad:51e1:d::1").address,InetAddress.getByName(if(version==4)"10.111.0.2" else "fdad:51e1:d::2").address,proto,segment)
    private fun udp(body:ByteArray)=ByteArray(8+body.size).apply{Wire.put16(this,0,40000);Wire.put16(this,2,53);Wire.put16(this,4,size);body.copyInto(this,8)}
    private fun tcp(sequence:Long,ack:Long,flags:Int,data:ByteArray=byteArrayOf())=ByteArray(20+data.size).apply{Wire.put16(this,0,40000);Wire.put16(this,2,53);Wire.put32(this,4,sequence);Wire.put32(this,8,ack);this[12]=0x50;this[13]=flags.toByte();Wire.put16(this,14,65535);data.copyInto(this,20)}
    @Test fun ipv4UdpRoundTrip(){val q=byteArrayOf(1,2,3,4,5);val p=checkNotNull(IpPacket.parse(ip(4,17,udp(q))));val response=checkNotNull(IpPacket.parse(p.udpResponse(q)));assertArrayEquals(p.source,response.destination);assertEquals(53,response.sourcePort);assertArrayEquals(q,response.transport.copyOfRange(8,response.transport.size))}
    @Test fun ipv6UdpRoundTrip(){val p=checkNotNull(IpPacket.parse(ip(6,17,udp(byteArrayOf(1,2,3)))));val r=checkNotNull(IpPacket.parse(p.udpResponse(byteArrayOf(9))));assertEquals(6,r.version);assertEquals(40000,r.destinationPort)}
    @Test fun checksumCorruptionRejected(){val b=ip(6,17,udp(byteArrayOf(1,2)));b[b.lastIndex]=(b.last().toInt() xor 1).toByte();assertNull(IpPacket.parse(b))}
    @Test fun truncatedIpRejected(){val b=ip(4,17,udp(byteArrayOf(1)));assertNull(IpPacket.parse(b.copyOf(b.size-1)))}
    @Test fun ipv4HeaderCorruptionRejected(){val b=ip(4,17,udp(byteArrayOf(1)));b[8]=1;assertNull(IpPacket.parse(b))}
    @Test fun tcpSplitFramesAndNoDoubleResolution(){
        val output=mutableListOf<IpPacket>();var calls=0
        val server=TcpDnsServer({calls++;it},{output+=checkNotNull(IpPacket.parse(it))},initialSequence={1000})
        fun send(seq:Long,ack:Long,flags:Int,data:ByteArray=byteArrayOf()){server.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(seq,ack,flags,data)))))}
        send(100,0,2);assertEquals(18,output.last().transport[13].toInt());assertEquals(101,Wire.u32(output.last().transport,8))
        send(101,1001,16)
        val frame=ByteArray(14).apply{Wire.put16(this,0,12);this[2]=99}
        send(101,1001,24,frame.copyOfRange(0,5));assertEquals(0,calls)
        send(106,1001,24,frame.copyOfRange(5,14));assertEquals(1,calls)
        val response=output.last().transport;assertEquals(1015,Wire.u32(response,4)+response.size-20);assertArrayEquals(frame,response.copyOfRange(20,response.size))
        send(106,1015,24,frame.copyOfRange(5,14));assertEquals(1,calls)
        send(115,1015,17);assertTrue(output.any{it.transport[13].toInt() and 1 != 0})
    }
    @Test fun tcpIPv6AndRetransmission(){var now=0L;val output=mutableListOf<ByteArray>();val s=TcpDnsServer({it},{output+=it},{now},{0xffff_ffffL});val syn=checkNotNull(IpPacket.parse(ip(6,6,tcp(42,0,2))));s.accept(syn);assertNotNull(IpPacket.parse(output.single()));now=1001;s.tick();assertEquals(2,output.size);assertArrayEquals(output[0],output[1]);s.accept(syn);assertEquals(3,output.size)}
    @Test fun unknownTcpFlowReset(){val output=mutableListOf<IpPacket>();val s=TcpDnsServer({it},{output+=checkNotNull(IpPacket.parse(it))});s.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(1,22,16)))));assertEquals(4,output.single().transport[13].toInt());assertEquals(22L,Wire.u32(output.single().transport,4))}
    @Test fun fuzzMalformedPackets(){val random=java.util.Random(7);repeat(3000){val b=ByteArray(random.nextInt(180));random.nextBytes(b);IpPacket.parse(b)}}
    @Test fun largeTcpResponseSegmentsWithoutCorruption(){
        val output=java.util.ArrayDeque<IpPacket>();val answer=ByteArray(60000){(it%251).toByte()};var calls=0
        val server=TcpDnsServer({calls++;answer},{output.add(checkNotNull(IpPacket.parse(it)))},initialSequence={500})
        fun send(seq:Long,ack:Long,flags:Int,data:ByteArray=byteArrayOf())=server.accept(checkNotNull(IpPacket.parse(ip(6,6,tcp(seq,ack,flags,data)))))
        send(200,0,2);output.clear();send(201,501,16)
        val frame=ByteArray(14).apply{Wire.put16(this,0,12)};send(201,501,24,frame)
        val received=java.io.ByteArrayOutputStream();var segments=0
        while(output.isNotEmpty()){
            val segment=output.remove().transport
            if(segment.size>20){
                val payload=segment.copyOfRange(20,segment.size);assertTrue(payload.size<=1200);received.write(payload);segments++
                send(215,(Wire.u32(segment,4)+payload.size) and 0xffffffffL,16)
            }
        }
        val result=received.toByteArray();assertEquals(60000,Wire.u16(result,0));assertArrayEquals(answer,result.copyOfRange(2,result.size));assertEquals(1,calls);assertTrue(segments>40)
    }
    @Test fun tcpOutOfOrderRequiresRetransmissionWithoutDuplicateDns(){
        val output=mutableListOf<IpPacket>();var calls=0
        val server=TcpDnsServer({calls++;it},{output+=checkNotNull(IpPacket.parse(it))},initialSequence={10})
        fun send(seq:Long,flags:Int,data:ByteArray=byteArrayOf())=server.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(seq,11,flags,data)))))
        send(100,2);send(101,16)
        val frame=ByteArray(14).apply{Wire.put16(this,0,12)}
        send(108,24,frame.copyOfRange(7,14));assertEquals(101L,Wire.u32(output.last().transport,8));assertEquals(0,calls)
        send(101,24,frame.copyOfRange(0,7));send(108,24,frame.copyOfRange(7,14));assertEquals(1,calls)
    }
    @Test fun tcpZeroWindowResumesWhenWindowOpens(){
        val out=mutableListOf<IpPacket>();val server=TcpDnsServer({it},{out+=checkNotNull(IpPacket.parse(it))},initialSequence={10})
        fun send(seq:Long,flags:Int,window:Int,data:ByteArray=byteArrayOf()){
            val segment=tcp(seq,11,flags,data);Wire.put16(segment,14,window)
            server.accept(checkNotNull(IpPacket.parse(ip(4,6,segment))))
        }
        send(100,2,0);send(101,16,0)
        val frame=ByteArray(14).apply{Wire.put16(this,0,12)};send(101,24,0,frame)
        assertEquals(20,out.last().transport.size);send(115,16,65535);assertTrue(out.last().transport.size>20)
    }
    @Test fun tcpIdleStateIsBounded(){
        var now=0L;val out=mutableListOf<IpPacket>();val server=TcpDnsServer({it},{out+=checkNotNull(IpPacket.parse(it))},{now},{100})
        server.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(10,0,2)))));now=31000;server.tick()
        server.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(11,101,16)))));assertEquals(4,out.last().transport[13].toInt())
    }
    @Test fun tcpHonorsAdvertisedMss(){
        val output=java.util.ArrayDeque<IpPacket>()
        val server=TcpDnsServer({ByteArray(500)},{output.add(checkNotNull(IpPacket.parse(it)))},initialSequence={100})
        val syn=ByteArray(24).apply{tcp(10,0,2).copyInto(this);this[12]=0x60;this[20]=2;this[21]=4;Wire.put16(this,22,64)}
        server.accept(checkNotNull(IpPacket.parse(ip(4,6,syn))));output.clear()
        server.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(11,101,16)))))
        val frame=ByteArray(14).apply{Wire.put16(this,0,12)}
        server.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(11,101,24,frame)))))
        var received=0
        while(output.isNotEmpty()){
            val segment=output.remove().transport
            if(segment.size>20){
                val size=segment.size-20;assertTrue(size<=64);received+=size
                server.accept(checkNotNull(IpPacket.parse(ip(4,6,tcp(25,Wire.u32(segment,4)+size,16)))))
            }
        }
        assertEquals(502,received)
    }
}
