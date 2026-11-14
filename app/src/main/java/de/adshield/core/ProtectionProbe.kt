package de.adshield.core

import org.xbill.DNS.*
import org.xbill.DNS.Record

/** Two known test queries only. Does not certify browser-specific DoH or ad-free content. */
object ProtectionProbe {
    enum class Result { HEALTHY, BLOCKING_FAILED, RESOLUTION_FAILED, INVALID_RESPONSE }
    fun run(blocked:String,allowed:String,exchange:(ByteArray)->ByteArray):Result {
        fun ask(domain:String):Message? {
            val query=Message.newQuery(Record.newRecord(Name(domain.trimEnd('.')+"."),Type.A,DClass.IN))
            val response=Message(exchange(query.toWire()))
            return response.takeIf{it.header.getFlag(Flags.QR.toInt()) && it.header.id==query.header.id && it.header.getCount(Section.QUESTION)==1 && it.question==query.question && it.header.opcode==Opcode.QUERY && !it.header.getFlag(Flags.TC.toInt())}
        }
        val local=ask(blocked)?:return Result.INVALID_RESPONSE
        if(local.rcode!=Rcode.NXDOMAIN || local.getSection(Section.AUTHORITY).filterIsInstance<SOARecord>().none{it.host==Name("blocked.invalid.")})return Result.BLOCKING_FAILED
        val remote=ask(allowed)?:return Result.INVALID_RESPONSE
        if(remote.rcode!=Rcode.NOERROR || remote.getSection(Section.ANSWER).none{it is ARecord})return Result.RESOLUTION_FAILED
        return Result.HEALTHY
    }
}
