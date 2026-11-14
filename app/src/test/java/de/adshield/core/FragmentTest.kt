package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class FragmentTest {
    private val payload=ByteArray(2500){(it%251).toByte()}
    private fun datagram(version:Int):ByteArray {
        val segment=ByteArray(payload.size+8).apply{Wire.put16(this,0,45000);Wire.put16(this,2,53);Wire.put16(this,4,size);payload.copyInto(this,8)}
        return IpPacket.encode(version,InetAddress.getByName(if(version==4)"10.111.0.1" else "fdad:51e1:d::1").address,InetAddress.getByName(if(version==4)"10.111.0.2" else "fdad:51e1:d::2").address,17,segment)
    }
    private fun fragment(version:Int,start:Int,length:Int,more:Boolean,id:Int=123):ByteArray {
        val full=datagram(version);val headerLength=if(version==4)20 else 40
        val part=full.copyOfRange(headerLength+start,headerLength+start+length)
        if(version==4)return full.copyOfRange(0,20).apply{Wire.put16(this,2,20+length);Wire.put16(this,4,id);Wire.put16(this,6,start/8 or if(more)0x2000 else 0);Wire.put16(this,10,0);Wire.put16(this,10,Wire.checksum(this))}+part
        val header=full.copyOfRange(0,40).apply{this[6]=44;Wire.put16(this,4,length+8)}
        return header+ByteArray(8).apply{this[0]=17;Wire.put16(this,2,start or if(more)1 else 0);Wire.put32(this,4,id.toLong())}+part
    }
    @Test fun ipv4OutOfOrderReassemblesWithValidChecksums(){
        val r=FragmentReassembler()
        assertNull(r.accept(fragment(4,1024,1024,true)));assertNull(r.accept(fragment(4,2048,460,false)))
        val result=checkNotNull(r.accept(fragment(4,0,1024,true)))
        val packet=checkNotNull(IpPacket.parse(result));assertArrayEquals(payload,packet.transport.copyOfRange(8,packet.transport.size))
    }
    @Test fun ipv6ReassemblesWithoutFragmentHeader(){
        val r=FragmentReassembler();assertNull(r.accept(fragment(6,0,1024,true)));assertNull(r.accept(fragment(6,1024,1024,true)))
        val packet=checkNotNull(IpPacket.parse(checkNotNull(r.accept(fragment(6,2048,460,false)))))
        assertEquals(6,packet.version);assertArrayEquals(payload,packet.transport.copyOfRange(8,packet.transport.size))
    }
    @Test fun overlappingIpv6DatagramIsDiscarded(){
        val r=FragmentReassembler();assertNull(r.accept(fragment(6,0,1024,true)));assertNull(r.accept(fragment(6,512,1024,true)))
        assertNull(r.accept(fragment(6,1024,1024,true)));assertNull(r.accept(fragment(6,2048,460,false)))
    }
    @Test fun partialDatagramExpires(){var now=0L;val r=FragmentReassembler{now};r.accept(fragment(4,0,1024,true));now=6000;r.accept(fragment(4,1024,1024,true));assertNull(r.accept(fragment(4,2048,460,false)))}
    @Test fun atomicIpv6Fragment(){val r=FragmentReassembler();assertNotNull(IpPacket.parse(checkNotNull(r.accept(fragment(6,0,2508,false)))))}
    @Test fun unfragmentedPacketPassesThrough(){val r=FragmentReassembler();assertArrayEquals(datagram(4),r.accept(datagram(4)));assertArrayEquals(datagram(6),r.accept(datagram(6)))}
    @Test fun fragmentLengthMustBeAlignedUnlessFinal(){val r=FragmentReassembler();assertNull(r.accept(fragment(4,0,1017,true)));assertNull(r.accept(fragment(4,1024,1024,true)));assertNull(r.accept(fragment(4,2048,460,false)))}
    @Test fun allocationAndMalformedInputAreBounded(){val r=FragmentReassembler();repeat(500){assertNull(r.accept(fragment(4,0,1024,true,it)))};val random=java.util.Random(9);repeat(1000){val b=ByteArray(random.nextInt(100));random.nextBytes(b);r.accept(b)};r.clear()}
}
