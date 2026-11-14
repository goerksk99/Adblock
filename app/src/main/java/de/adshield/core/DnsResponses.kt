package de.adshield.core

import org.xbill.DNS.*

internal object DnsResponses {
    fun error(query:Message,code:Int)=Message(query.header.id).apply {
        header.setFlag(Flags.QR.toInt());header.setFlag(Flags.RA.toInt())
        if(query.header.getFlag(Flags.RD.toInt()))header.setFlag(Flags.RD.toInt())
        if(query.header.getFlag(Flags.CD.toInt()))header.setFlag(Flags.CD.toInt())
        header.rcode=code;query.question?.let{addRecord(it,Section.QUESTION)}
    }
    fun bare(bytes:ByteArray)=ByteArray(12).apply{if(bytes.size>=2){this[0]=bytes[0];this[1]=bytes[1]};this[2]=0x80.toByte();this[3]=Rcode.FORMERR.toByte()}
    fun unavailable(bytes:ByteArray):DnsResult {
        val wire=try{error(Message(bytes),Rcode.SERVFAIL).toWire(512)}catch(_:Exception){bare(bytes)}
        return DnsResult(wire,Outcome.ERROR)
    }
}
