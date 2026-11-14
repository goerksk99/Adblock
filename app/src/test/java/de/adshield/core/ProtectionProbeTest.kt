package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import java.net.InetAddress

class ProtectionProbeTest {
    private fun response(raw:ByteArray):ByteArray=Message(raw).apply {
        header.setFlag(Flags.QR.toInt())
        if(question.name.toString()=="ads.example."){header.rcode=Rcode.NXDOMAIN;addRecord(SOARecord(question.name,DClass.IN,60,Name("blocked.invalid."),Name("hostmaster.blocked.invalid."),1,60,60,60,60),Section.AUTHORITY)}
        else addRecord(ARecord(question.name,DClass.IN,60,InetAddress.getByName("192.0.2.1")),Section.ANSWER)
    }.toWire()
    @Test fun realBlockingAndResolutionAreBothRequired(){
        var calls=0
        assertEquals(ProtectionProbe.Result.HEALTHY,ProtectionProbe.run("ads.example","example.com"){calls++;response(it)})
        assertEquals(2,calls)
    }
    @Test fun reportsMissingBlockInsteadOfFalseSuccess(){
        assertEquals(ProtectionProbe.Result.BLOCKING_FAILED,ProtectionProbe.run("ads.example","example.com"){raw->Message(raw).apply{header.setFlag(Flags.QR.toInt())}.toWire()})
    }
    @Test fun mismatchedIdentityCannotPass(){
        assertEquals(ProtectionProbe.Result.INVALID_RESPONSE,ProtectionProbe.run("ads.example","example.com"){response(it).apply{this[0]=(this[0].toInt() xor 255).toByte()}})
    }
    @Test fun resolutionFailureDistinguishedFromBlockingFailure(){
        assertEquals(ProtectionProbe.Result.RESOLUTION_FAILED,ProtectionProbe.run("ads.example","example.com"){raw->Message(response(raw)).apply{if(question.name.toString()!="ads.example."){removeAllRecords(Section.ANSWER);header.rcode=Rcode.SERVFAIL}}.toWire()})
    }
    @Test fun upstreamNxdomainAloneCannotPretendToBeLocalBlocking(){
        assertEquals(ProtectionProbe.Result.BLOCKING_FAILED,ProtectionProbe.run("ads.example","example.com"){raw ->Message(response(raw)).apply{removeAllRecords(Section.AUTHORITY)}.toWire()})
    }
}
