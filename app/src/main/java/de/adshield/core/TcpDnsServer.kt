package de.adshield.core

import java.security.SecureRandom

/** A bounded TCP endpoint only for the two synthetic DNS addresses. Not a general IP forwarder.
 * In-order receive, duplicate ACKs, segmentation, advertised-window handling, retransmission,
 * length-prefixed DNS messages and orderly half-close. All state is serialized by this object.
 */
class TcpDnsServer(
    private val resolve: (ByteArray) -> ByteArray,
    private val emit: (ByteArray) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val initialSequence: () -> Long = { SecureRandom().nextInt().toLong() and MASK }
) {
    private data class Pending(val packet: ByteArray, val end: Long, var sent: Long, var retries: Int=0)
    private data class Session(val packet: IpPacket, var receive: Long, var send: Long, var last: Long,
        var window: Int, val mss:Int=536, var established: Boolean=false, var input: ByteArray=byteArrayOf(),
        var output: ByteArray=byteArrayOf(), var pending: Pending?=null, var peerClosed:Boolean=false, var finSent:Boolean=false)
    private val sessions=HashMap<String,Session>()
    private fun key(p:IpPacket)="${p.version}:${p.source.joinToString(",")}:${p.sourcePort}:${p.destination.joinToString(",")}"
    @Synchronized fun accept(p:IpPacket) {
        if(p.protocol!=6 || p.destinationPort!=53)return
        val t=p.transport;val flags=t[13].toInt() and 255;val sequence=Wire.u32(t,4);val ack=Wire.u32(t,8)
        val header=(t[12].toInt() ushr 4 and 15)*4;val data=t.copyOfRange(header,t.size)
        val k=key(p);var s=sessions[k]
        if(flags and RST!=0){sessions.remove(k);return}
        if(flags and SYN!=0) {
            if(flags and ACK!=0) { reset(p);return }
            if(s!=null && s.receive==plus(sequence,1) && !s.established) { s.pending?.let{emit(it.packet)};return }
            if(sessions.size>=128) { reset(p);return }
            val mss=offeredMss(p)?:run{reset(p);return}
            s=Session(p,plus(sequence,1),initialSequence(),clock(),Wire.u16(t,14),mss=mss)
            sessions[k]=s;send(s,SYN or ACK,byteArrayOf());return
        }
        if(s==null){reset(p);return}
        s.last=clock()
        if(flags and ACK==0)return
        val outstanding=s.pending
        if(outstanding!=null && ack==outstanding.end) {
            s.pending=null
            if(!s.established)s.established=true
            if(s.finSent){sessions.remove(k);return}
        } else if(ack!=s.send) { return }
        s.window=Wire.u16(t,14)
        if(!s.established)return
        if(sequence!=s.receive){acknowledge(s);flush(s);return}
        if(data.isNotEmpty()) {
            if(s.peerClosed || s.input.size+data.size>65537){reset(p);sessions.remove(k);return}
            s.input+=data;s.receive=plus(s.receive,data.size)
            acknowledge(s)
            while(s.input.size>=2) {
                val length=Wire.u16(s.input,0)
                if(length<12){reset(p);sessions.remove(k);return}
                if(s.input.size<length+2)break
                val query=s.input.copyOfRange(2,2+length);s.input=s.input.copyOfRange(length+2,s.input.size)
                val answer=resolve(query)
                if(answer.size>65535 || s.output.size+answer.size+2>131074){reset(p);sessions.remove(k);return}
                val framed=ByteArray(answer.size+2);Wire.put16(framed,0,answer.size);answer.copyInto(framed,2);s.output+=framed
            }
        }
        if(flags and FIN!=0){s.receive=plus(s.receive,1);s.peerClosed=true;acknowledge(s)}
        flush(s)
    }
    private fun offeredMss(packet:IpPacket):Int? {
        val segment=packet.transport
        val end=(segment[12].toInt() ushr 4 and 15)*4
        var at=20
        var mss=if(packet.version==4)536 else 1200
        while(at<end){
            val kind=segment[at].toInt() and 255
            if(kind==0)break
            if(kind==1){at++;continue}
            if(at+2>end)return null
            val length=segment[at+1].toInt() and 255
            if(length<2 || at+length>end)return null
            if(kind==2){
                if(length!=4)return null
                val offered=Wire.u16(segment,at+2)
                if(offered==0)return null
                mss=minOf(offered,1200)
            }
            at+=length
        }
        return mss
    }
    private fun flush(s:Session) {
        if(s.pending!=null)return
        if(s.output.isNotEmpty() && s.window>0) {
            val length=minOf(s.mss,s.window,s.output.size)
            val data=s.output.copyOfRange(0,length);s.output=s.output.copyOfRange(length,s.output.size)
            send(s,PSH or ACK,data)
        } else if(s.peerClosed && s.output.isEmpty() && !s.finSent) {s.finSent=true;send(s,FIN or ACK,byteArrayOf())}
    }
    @Synchronized fun tick() {
        val now=clock();val iterator=sessions.iterator()
        while(iterator.hasNext()) {
            val s=iterator.next().value
            val pending=s.pending
            if(now-s.last>30000 || (pending!=null && pending.retries>=4)) { iterator.remove();continue }
            if(pending!=null && now-pending.sent>=1000L shl pending.retries.coerceAtMost(3)) {
                emit(pending.packet);pending.sent=now;pending.retries++
            }
        }
    }
    @Synchronized fun close() { sessions.clear() }
    @Synchronized fun hasSessions():Boolean = sessions.isNotEmpty()
    private fun acknowledge(s:Session) { emit(segment(s.packet,s.send,s.receive,ACK,byteArrayOf())) }
    private fun send(s:Session,flags:Int,data:ByteArray) {
        val packet=segment(s.packet,s.send,s.receive,flags,data)
        s.send=plus(s.send,data.size+if(flags and (SYN or FIN)!=0)1 else 0)
        s.pending=Pending(packet,s.send,clock());emit(packet)
    }
    private fun reset(p:IpPacket) {
        val flags=p.transport[13].toInt() and 255
        if(flags and ACK!=0)emit(segment(p,Wire.u32(p.transport,8),0,RST,byteArrayOf()))
        else {
            val size=p.transport.size-(p.transport[12].toInt() ushr 4 and 15)*4
            emit(segment(p,0,plus(Wire.u32(p.transport,4),size+if(flags and (SYN or FIN)!=0)1 else 0),RST or ACK,byteArrayOf()))
        }
    }
    private fun segment(p:IpPacket,sequence:Long,ack:Long,flags:Int,data:ByteArray):ByteArray {
        val t=ByteArray(20+data.size)
        Wire.put16(t,0,p.destinationPort);Wire.put16(t,2,p.sourcePort)
        Wire.put32(t,4,sequence);Wire.put32(t,8,ack);t[12]=0x50;t[13]=flags.toByte()
        Wire.put16(t,14,65535);data.copyInto(t,20)
        return p.reverse(t)
    }
    companion object {
        private const val MASK=0xffffffffL
        const val FIN=1;const val SYN=2;const val RST=4;const val PSH=8;const val ACK=16
        private fun plus(v:Long,n:Int)=(v+n) and MASK
    }
}
