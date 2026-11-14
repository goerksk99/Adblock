package de.adshield.vpn

import android.net.VpnService
import de.adshield.core.DnsUpstream
import de.adshield.core.ProtectedSocketFactory
import de.adshield.core.DnsHttpException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.*
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

interface ManagedUpstream:DnsUpstream { fun networkChanged();fun close() }

/** No DNS bootstrap through the VPN. HTTPS hostname/SNI and certificate checks stay enabled. */
class Quad9(service:VpnService):ManagedUpstream {
    private val factory=ProtectedSocketFactory { socket -> service.protect(socket) }
    private val client=OkHttpClient.Builder().socketFactory(factory)
        .dns(object:Dns { override fun lookup(hostname:String):List<InetAddress> { require(hostname=="dns.quad9.net");return listOf("9.9.9.9","149.112.112.112","2620:fe::fe","2620:fe::9").map{InetAddress.getByName(it)} } })
        .connectTimeout(3,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS).callTimeout(6,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    override fun exchange(query:ByteArray):ByteArray {
        val request=Request.Builder().url("https://dns.quad9.net/dns-query")
            .header("Accept","application/dns-message").post(query.toRequestBody("application/dns-message".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            if(!response.isSuccessful)throw DnsHttpException(response.code)
            if(!response.header("Content-Type")?.substringBefore(';')?.trim().equals("application/dns-message",true))throw ProtocolException("Ungültiger DNS-Inhaltstyp")
            val body=checkNotNull(response.body);if(body.contentLength()>65535)throw ProtocolException("DNS-Antwort zu groß")
            val source=body.source();val buffer=okio.Buffer();var size=0L
            while(true){val n=source.read(buffer,8192);if(n<0)break;size+=n;if(size>65535)throw ProtocolException("DNS-Antwort zu groß")}
            buffer.readByteArray()
        }
    }
    override fun networkChanged(){client.connectionPool.evictAll()}
    override fun close(){client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
}
