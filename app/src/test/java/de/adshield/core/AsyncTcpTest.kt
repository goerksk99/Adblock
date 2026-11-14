package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.net.InetAddress

class AsyncTcpTest {
    private val output=mutableListOf<IpPacket>()
    private val tasks=mutableListOf<()->Unit>()
    private val query=Message.newQuery(Record.newRecord(Name("example.com."),Type.A,DClass.IN)).toWire()
    private val frame=ByteArray(query.size+2).apply{Wire.put16(this,0,query.size);query.copyInto(this,2)}
    private fun packet(port:Int,seq:Long,ack:Long,flags:Int,data:ByteArray=byteArrayOf()):IpPacket {
        val segment=ByteArray(20+data.size).apply{Wire.put16(this,0,port);Wire.put16(this,2,53);Wire.put32(this,4,seq);Wire.put32(this,8,ack);this[12]=0x50;this[13]=flags.toByte();Wire.put16(this,14,65535);data.copyInto(this,20)}
        return checkNotNull(IpPacket.parse(IpPacket.encode(4,InetAddress.getByName("10.111.0.1").address,InetAddress.getByName("10.111.0.2").address,6,segment)))
    }
    private fun handshake(server:TcpDnsServer,port:Int){server.accept(packet(port,100,0,2));server.accept(packet(port,101,1001,16))}
    private fun server(resolve:(ByteArray)->ByteArray={it},dispatch:((()->Unit,()->Unit)->Unit)={task,_->tasks.add(task)})=
        TcpDnsServer(resolve,{output.add(checkNotNull(IpPacket.parse(it)))},initialSequence={1000},dispatch=dispatch)
    @Test fun otherFlowsProgressWhileFirstResolutionIsPending(){
        val server=server();handshake(server,40000);server.accept(packet(40000,101,1001,24,frame))
        handshake(server,40001);server.accept(packet(40001,101,1001,24,frame));assertEquals(2,tasks.size)
        tasks[1]();assertEquals(40001,output.last().destinationPort);assertTrue(output.last().transport.size>20)
        tasks[0]();assertEquals(40000,output.last().destinationPort)
    }
    @Test fun closedSessionDoesNotStartDeferredUpstreamWork(){
        var calls=0;val server=server(resolve={calls++;it});handshake(server,40000)
        server.accept(packet(40000,101,1001,24,frame));server.close();val before=output.size
        tasks.single()();assertEquals(0,calls);assertEquals(before,output.size)
    }
    @Test fun rejectedWorkReturnsFramedServfail(){
        val server=server(resolve={error("Upstream must not run")},dispatch={_,reject->reject()})
        handshake(server,40000);server.accept(packet(40000,101,1001,24,frame))
        val body=output.last().transport.copyOfRange(20,output.last().transport.size)
        assertEquals(Rcode.SERVFAIL,Message(body.copyOfRange(2,body.size)).rcode)
    }
    @Test fun halfCloseWaitsForPendingResolution(){
        val server=server();handshake(server,40000);server.accept(packet(40000,101,1001,25,frame))
        assertTrue(output.none{it.transport[13].toInt() and 1!=0})
        tasks.single()();val data=output.last().transport
        val end=Wire.u32(data,4)+data.size-20
        server.accept(packet(40000,102+frame.size.toLong(),end,16))
        assertTrue(output.last().transport[13].toInt() and 1!=0)
    }
}
