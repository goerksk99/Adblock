package de.adshield.core

object Wire {
    fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
    fun u32(b: ByteArray, p: Int) = ((u16(b,p).toLong() shl 16) or u16(b,p+2).toLong())
    fun put16(b: ByteArray, p: Int, v: Int) { b[p]=(v ushr 8).toByte(); b[p+1]=v.toByte() }
    fun put32(b: ByteArray, p: Int, v: Long) { put16(b,p,(v ushr 16).toInt());put16(b,p+2,v.toInt()) }
    fun checksum(b: ByteArray): Int {
        var sum=0L
        var p=0
        while(p+1<b.size) { sum += u16(b,p); p+=2 }
        if(p<b.size) sum+=(b[p].toInt() and 255) shl 8
        while(sum ushr 16 != 0L) sum=(sum and 65535)+(sum ushr 16)
        return sum.toInt().inv() and 65535
    }
}
data class IpPacket(val version: Int, val source: ByteArray, val destination: ByteArray, val protocol: Int, val transport: ByteArray) {
    val sourcePort get()=Wire.u16(transport,0)
    val destinationPort get()=Wire.u16(transport,2)
    fun reverse(segment: ByteArray): ByteArray = encode(version,destination,source,protocol,segment)
    companion object {
        fun parse(bytes: ByteArray): IpPacket? = runCatching {
            require(bytes.size>=20)
            val version=bytes[0].toInt() ushr 4 and 15
            val offset:Int; val end:Int; val source:ByteArray;val dest:ByteArray;val proto:Int
            if(version==4) {
                offset=(bytes[0].toInt() and 15)*4;end=Wire.u16(bytes,2)
                require(offset>=20 && end<=bytes.size && end>=offset && Wire.u16(bytes,6) and 0x3fff == 0)
                require(Wire.checksum(bytes.copyOfRange(0,offset))==0)
                source=bytes.copyOfRange(12,16);dest=bytes.copyOfRange(16,20);proto=bytes[9].toInt() and 255
            } else {
                require(version==6 && bytes.size>=40)
                end=40+Wire.u16(bytes,4);require(end<=bytes.size)
                source=bytes.copyOfRange(8,24);dest=bytes.copyOfRange(24,40)
                var next=bytes[6].toInt() and 255;var at=40;var extensions=0
                while(next in setOf(0,43,60)) {
                    require(++extensions<=8 && at+2<=end)
                    val size=((bytes[at+1].toInt() and 255)+1)*8
                    next=bytes[at].toInt() and 255;at+=size;require(at<=end)
                }
                require(next!=44) // fragmented DNS is not accepted; EDNS is capped to 1232.
                offset=at;proto=next
            }
            require(proto==6 || proto==17)
            val segment=bytes.copyOfRange(offset,end)
            require(segment.size>=if(proto==6)20 else 8)
            if(proto==17) { require(Wire.u16(segment,4)==segment.size); if(version==6)require(Wire.u16(segment,6)!=0) }
            if(proto==6) require((segment[12].toInt() ushr 4 and 15)*4 in 20..segment.size)
            if(proto==6 || version==6 || Wire.u16(segment,6)!=0) require(Wire.checksum(pseudo(version,source,dest,proto,segment))==0)
            IpPacket(version,source,dest,proto,segment)
        }.getOrNull()
        private fun pseudo(version:Int,source:ByteArray,dest:ByteArray,proto:Int,segment:ByteArray):ByteArray {
            val h=ByteArray(if(version==4)12 else 40)
            source.copyInto(h);dest.copyInto(h,source.size)
            if(version==4){h[9]=proto.toByte();Wire.put16(h,10,segment.size)}
            else {Wire.put32(h,32,segment.size.toLong());h[39]=proto.toByte()}
            return h+segment
        }
        fun encode(version:Int,source:ByteArray,dest:ByteArray,proto:Int,segment:ByteArray):ByteArray {
            val s=segment.copyOf();val check=if(proto==17)6 else 16
            Wire.put16(s,check,0)
            val c=Wire.checksum(pseudo(version,source,dest,proto,s))
            Wire.put16(s,check,if(c==0 && proto==17)65535 else c)
            val h=ByteArray(if(version==4)20 else 40)
            h[0]=if(version==4)0x45 else 0x60
            if(version==4){Wire.put16(h,2,h.size+s.size);h[8]=64;h[9]=proto.toByte();source.copyInto(h,12);dest.copyInto(h,16);Wire.put16(h,10,Wire.checksum(h))}
            else {Wire.put16(h,4,s.size);h[6]=proto.toByte();h[7]=64;source.copyInto(h,8);dest.copyInto(h,24)}
            return h+s
        }
    }
    fun udpResponse(dns: ByteArray): ByteArray {
        require(protocol==17)
        val s=ByteArray(8+dns.size)
        Wire.put16(s,0,destinationPort);Wire.put16(s,2,sourcePort);Wire.put16(s,4,s.size);dns.copyInto(s,8)
        return reverse(s)
    }
}
