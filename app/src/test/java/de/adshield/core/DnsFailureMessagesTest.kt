package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.xbill.DNS.*
import org.xbill.DNS.Record

class DnsFailureMessagesTest {
    @Test fun servfailRetainsTransportCauseForStartupDiagnostic(){
        val cause=SocketProtectionException()
        val engine=DnsEngine(FilterEngine(),{throw cause})
        val query=Message.newQuery(Record.newRecord(Name("example.com."),Type.A,DClass.IN))
        val result=engine.resolve(query.toWire(),filtering=false)
        assertEquals(Rcode.SERVFAIL,Message(result.wire).rcode)
        assertTrue(result.transportFailure);assertSame(cause,result.failureCause)
        assertTrue(DnsFailureMessages.describe(result.failureCause).contains("Socket"))
    }
    @Test fun tlsAndTimeoutAreDifferentAndNeverExposeRawExceptionText(){
        val tls=DnsFailureMessages.describe(IOException("private.example",SSLHandshakeException("private.example")))
        assertTrue(tls.contains("Zertifikatsprüfung"));assertFalse(tls.contains("private.example"))
        val timeout=DnsFailureMessages.describe(SocketTimeoutException("private.example"))
        assertTrue(timeout.contains("Zeitüberschreitung"));assertFalse(timeout.contains("private.example"))
        assertNotEquals(tls,timeout)
        assertTrue(DnsFailureMessages.describe(DnsHttpException(503)).contains("503"))
    }
}
