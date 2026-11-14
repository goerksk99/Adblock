package de.adshield.core

/** RFC 8200/5722-style conservative reassembly for DNS-only TUN input.
 * Overlaps invalidate a whole datagram. State and lifetime are strictly bounded.
 * Unfragmented packets pass through for the full transport/checksum validator.
 */
class FragmentReassembler(private val clock:()->Long={System.nanoTime()/1_000_000}) {
    private data class Part(val start:Int,val bytes:ByteArray)
    private data class Assembly(val created:Long,val version:Int,val parts:MutableList<Part> = mutableListOf(),
        var header:ByteArray?=null,var finalSize:Int?=null,var nextPointer:Int=0,var nextProtocol:Int=0)
    private val pending=LinkedHashMap<String,Assembly>()
    private val rejected=LinkedHashMap<String,Long>()
    private var retained=0
    @Synchronized fun accept(packet:ByteArray):ByteArray? = runCatching {
        val now=clock()
        val iterator=pending.iterator()
        while(iterator.hasNext()){
            val item=iterator.next()
            if(now-item.value.created>=5000){retained-=item.value.parts.sumOf{it.bytes.size};iterator.remove()}
        }
        rejected.entries.removeAll{now-it.value>=5000}
        require(packet.isNotEmpty())
        val version=packet[0].toInt() ushr 4 and 15
        val header:ByteArray;val data:ByteArray;val offset:Int;val more:Boolean;val key:String;val pointer:Int;val protocol:Int
        when(version){
            4 -> {
                require(packet.size>=20)
                val ihl=(packet[0].toInt() and 15)*4;val size=Wire.u16(packet,2)
                require(ihl>=20 && size in ihl..packet.size && Wire.checksum(packet.copyOfRange(0,ihl))==0)
                val bits=Wire.u16(packet,6)
                if(bits and 0x3fff==0)return packet
                require(bits and 0x8000==0 && bits and 0x4000==0)
                offset=(bits and 0x1fff)*8;more=bits and 0x2000!=0
                protocol=packet[9].toInt() and 255;pointer=9
                require(protocol==6 || protocol==17)
                key="4:${packet.copyOfRange(12,20).joinToString(",")}:${Wire.u16(packet,4)}:$protocol"
                header=packet.copyOfRange(0,ihl);data=packet.copyOfRange(ihl,size)
            }
            6 -> {
                require(packet.size>=40)
                val size=40+Wire.u16(packet,4);require(size<=packet.size)
                var next=packet[6].toInt() and 255;var at=40;var previous=6;var count=0
                while(next in setOf(0,43,60)){
                    require(++count<=8 && at+2<=size)
                    val length=((packet[at+1].toInt() and 255)+1)*8
                    next=packet[at].toInt() and 255;previous=at;at+=length;require(at<=size)
                }
                if(next!=44)return packet
                require(at+8<=size)
                val bits=Wire.u16(packet,at+2);require(bits and 6==0)
                offset=bits and 0xfff8;more=bits and 1!=0
                protocol=packet[at].toInt() and 255;pointer=previous
                require(protocol==6 || protocol==17 || protocol==60)
                key="6:${packet.copyOfRange(8,40).joinToString(",")}:${Wire.u32(packet,at+4)}"
                header=packet.copyOfRange(0,at);data=packet.copyOfRange(at+8,size)
            }
            else -> return null
        }
        require(data.isNotEmpty() && offset+data.size<=65535)
        require(!more || data.size%8==0)
        if(rejected.containsKey(key))return null
        if(!pending.containsKey(key) && pending.size>=64)return null
        val state=pending.getOrPut(key){Assembly(now,version)}
        fun discard():ByteArray? {
            retained-=state.parts.sumOf{it.bytes.size};pending.remove(key)
            if(rejected.size<128)rejected[key]=now
            return null
        }
        if(state.parts.size>=128 || retained+data.size>1024*1024)return discard()
        if(state.parts.any{offset<it.start+it.bytes.size && it.start<offset+data.size})return discard()
        if(state.finalSize?.let{offset+data.size>it}==true)return discard()
        if(!more){
            if(state.finalSize!=null && state.finalSize!=offset+data.size)return discard()
            state.finalSize=offset+data.size
            if(state.parts.any{it.start+it.bytes.size>offset+data.size})return discard()
        }
        if(offset==0){state.header=header;state.nextPointer=pointer;state.nextProtocol=protocol}
        state.parts+=Part(offset,data);retained+=data.size
        val first=state.header?:return null;val final=state.finalSize?:return null
        val parts=state.parts.sortedBy{it.start};var position=0
        for(part in parts){if(part.start!=position)return null;position+=part.bytes.size}
        if(position!=final)return null
        if(version==4 && first.size+final>65535)return discard()
        if(version==6 && first.size-40+final>65535)return discard()
        val result=ByteArray(first.size+final);first.copyInto(result)
        parts.forEach{it.bytes.copyInto(result,first.size+it.start)}
        if(version==4){Wire.put16(result,2,result.size);Wire.put16(result,6,0);Wire.put16(result,10,0);Wire.put16(result,10,Wire.checksum(result.copyOfRange(0,first.size)))}
        else {result[state.nextPointer]=state.nextProtocol.toByte();Wire.put16(result,4,result.size-40)}
        retained-=state.parts.sumOf{it.bytes.size};pending.remove(key)
        result
    }.getOrNull()
    @Synchronized fun clear(){pending.clear();rejected.clear();retained=0}
}
