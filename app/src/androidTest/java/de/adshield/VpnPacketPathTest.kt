package de.adshield

import android.content.Context
import android.content.Intent
import android.net.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.adshield.vpn.*
import de.adshield.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*

/** Exercises Android's real TUN, routes, UDP and TCP with a deterministic DNS upstream.
 * Does not claim to test Quad9's TLS/socket-protection behavior on Android.
 */
@RunWith(AndroidJUnit4::class)
class VpnPacketPathTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private var tunnelNetwork:Network?=null
    @Test fun realTunIpv4Ipv6UdpTcpRoutesAndShutdown()=runBlocking {
        val app=context as AdShieldApp
        withTimeout(180000){app.ready.await()}
        val before=app.store.settings.first()
        app.store.maximal(true);app.store.dnsGuard(true)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val command=automation.executeShellCommand("appops set de.adshield ACTIVATE_VPN allow")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).bufferedReader().use{it.readText()}
        assertNull("Test VPN authorization must be granted by the shell",VpnService.prepare(context))
        val unavailable=java.util.concurrent.atomic.AtomicBoolean(false)
        val loadGate=java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch?>(null)
        val loadEntered=java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))
        val resolver=object:ManagedUpstream {
            override fun exchange(query:ByteArray):ByteArray {
                val name=Message(query).question.name.toString()
                val gate=loadGate.get()
                if(gate!=null && name.startsWith("queue-load-")){
                    loadEntered.get().countDown();check(gate.await(60,java.util.concurrent.TimeUnit.SECONDS)){"Load test gate timed out"}
                }
                if(unavailable.get())throw java.net.SocketTimeoutException("Controlled test failure")
                return Message(query).apply {
                header.setFlag(Flags.QR.toInt());header.setFlag(Flags.RA.toInt())
                val question=question
                if(question.name==Name("https-alias-test.invalid.") && question.type==Type.HTTPS)addRecord(HTTPSRecord(question.name,DClass.IN,60,0,Name("doubleclick.net."),emptyList()),Section.ANSWER)
                if(question.name==Name("svcb-alias-test.invalid.") && question.type==Type.SVCB)addRecord(SVCBRecord(question.name,DClass.IN,60,0,Name("doubleclick.net."),emptyList()),Section.ANSWER)
                val owner=if(question.name==Name("sub.dname-test.invalid.")){
                    addRecord(DNAMERecord(Name("dname-test.invalid."),DClass.IN,60,Name("doubleclick.net.")),Section.ANSWER)
                    Name("sub.doubleclick.net.")
                }else if(question.name==Name("adshield-cloak-test.invalid.")) {
                    addRecord(CNAMERecord(question.name,DClass.IN,60,Name("doubleclick.net.")),Section.ANSWER)
                    Name("doubleclick.net.")
                } else question.name
                if(question.type==Type.A)addRecord(ARecord(owner,DClass.IN,60,InetAddress.getByName("192.0.2.7")),Section.ANSWER)
                else if(question.type==Type.AAAA)addRecord(AAAARecord(owner,DClass.IN,60,InetAddress.getByName("2001:db8::7")),Section.ANSWER)
            }.toWire()
            }
            override fun networkChanged(){}
            override fun close(){}
        }
        app.instrumentationUpstream=resolver
        val received=java.util.concurrent.atomic.AtomicInteger()
        app.instrumentationPacketObserver={packet ->
            val number=received.incrementAndGet()
            if(number<=12){val parsed=IpPacket.parse(packet);println("TUN_INPUT bytes=${packet.size} version=${packet[0].toInt() ushr 4 and 15} parsed=${parsed!=null}")}
        }
        try {
            if(android.os.Build.VERSION.SDK_INT>=31){context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));delay(3000)}
            context.startForegroundService(Intent(context,DnsVpnService::class.java).setAction(DnsVpnService.START))
            val state=withTimeout(120000){DnsVpnService.state.first{it.stage in setOf(Stage.ACTIVE,Stage.ERROR)}}
            assertEquals(state.message,Stage.ACTIVE,state.stage)
            val cm=context.getSystemService(ConnectivityManager::class.java)
            val vpn=withTimeout(30000){
                while(true){
                    val network=cm.allNetworks.firstOrNull{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}
                    if(network!=null)return@withTimeout network
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
            tunnelNetwork=vpn
            val links=checkNotNull(cm.getLinkProperties(vpn))
            val routes=links.routes.filter{it.type==RouteInfo.RTN_UNICAST}.map{it.destination.toString()}.toSet()
            assertTrue(routes.toString(),"10.111.0.2/32" in routes)
            assertTrue(routes.toString(),routes.any{it.endsWith("/128")&&it.contains("fdad:51e1:d:")})
            assertFalse(routes.toString(),"0.0.0.0/0" in routes || "::/0" in routes)
            assertTrue(routes.toString(),"8.8.8.8/32" in routes)
            assertTrue(routes.toString(),routes.size>=22)
            assertTrue(app.engine.blocked("dns.google"))
            println("VPN_LINKS ${links}")
            for(address in listOf(DnsVpnService.DNS4,DnsVpnService.DNS6,"8.8.8.8","2001:4860:4860::8888")){
                val allowed=udp(address,"example.com.",Type.A)
                assertEquals(Rcode.NOERROR,allowed.rcode);assertEquals("192.0.2.7",(allowed.getSection(Section.ANSWER).first() as ARecord).address.hostAddress)
                assertEquals(Rcode.NXDOMAIN,udp(address,"doubleclick.net.",Type.A).rcode)
                assertEquals(Rcode.NXDOMAIN,udp(address,"adshield-cloak-test.invalid.",Type.A).rcode)
                assertEquals(Rcode.NXDOMAIN,tcp(address,"adshield-cloak-test.invalid.",Type.AAAA).rcode)
                for((alias,type)in listOf("sub.dname-test.invalid." to Type.A,"https-alias-test.invalid." to Type.HTTPS,"svcb-alias-test.invalid." to Type.SVCB)){
                    assertEquals(alias,Rcode.NXDOMAIN,udp(address,alias,type).rcode)
                    assertEquals(alias,Rcode.NXDOMAIN,tcp(address,alias,type).rcode)
                }
                val v6=tcp(address,"example.com.",Type.AAAA)
                assertEquals(Rcode.NOERROR,v6.rcode);assertTrue(v6.getSection(Section.ANSWER).first() is AAAARecord)
            }
            val owner=object:androidx.lifecycle.ViewModelStoreOwner {override val viewModelStore=androidx.lifecycle.ViewModelStore()}
            val modelRef=java.util.concurrent.atomic.AtomicReference<de.adshield.ui.ShieldModel>()
            automation.let { InstrumentationRegistry.getInstrumentation().runOnMainSync {
                modelRef.set(androidx.lifecycle.ViewModelProvider(owner,androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))[de.adshield.ui.ShieldModel::class.java])
            }}
            try {
                val model=modelRef.get();model.diagnose()
                withTimeout(30000){while(model.diagnosing.value)delay(50)}
                assertTrue(model.diagnostic.value,model.diagnostic.value.startsWith("Erfolgreich:"))
                println("IN_APP_DIAGNOSTIC_PASSED")
            }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync{owner.viewModelStore.clear()}}
            // QUIC/DoH traffic at a captured resolver must be discarded, not sent to the DNS resolver.
            val beforeEncrypted=received.get()
            DatagramSocket().use { socket ->
                vpn.bindSocket(socket);socket.soTimeout=1000
                val payload=question("encrypted-probe.invalid.",Type.A)
                socket.send(DatagramPacket(payload,payload.size,InetAddress.getByName("8.8.8.8"),443))
                try {socket.receive(DatagramPacket(ByteArray(4096),4096));fail("Non-DNS resolver port must not receive DNS replies")}
                catch(expected:SocketTimeoutException) { /* expected: no forwarding, no answer */ }
            }
            assertTrue("Encrypted-port probe must reach TUN",received.get()>beforeEncrypted)
            println("CAPTURED_RESOLVER_NON_DNS_DROPPED")
            // Two blocked TCP operations must not starve independent UDP work.
            val clients=java.util.concurrent.Executors.newFixedThreadPool(2)
            val tcpGate=java.util.concurrent.CountDownLatch(1)
            loadGate.set(tcpGate);loadEntered.set(java.util.concurrent.CountDownLatch(1))
            try {
                val one=clients.submit<Message>{tcp(DnsVpnService.DNS4,"queue-load-one.invalid.",Type.A)}
                assertTrue(loadEntered.get().await(30,java.util.concurrent.TimeUnit.SECONDS))
                val two=clients.submit<Message>{tcp(DnsVpnService.DNS4,"queue-load-two.invalid.",Type.A)}
                delay(300)
                assertEquals(Rcode.NOERROR,udp(DnsVpnService.DNS4,"example.org.",Type.A).rcode)
                tcpGate.countDown();one.get(30,java.util.concurrent.TimeUnit.SECONDS);two.get(30,java.util.concurrent.TimeUnit.SECONDS)
            }finally{tcpGate.countDown();loadGate.set(null);clients.shutdownNow()}
            println("UDP_SURVIVES_STALLED_TCP")
            val udpGate=java.util.concurrent.CountDownLatch(1)
            loadGate.set(udpGate);loadEntered.set(java.util.concurrent.CountDownLatch(1))
            try {
                DatagramSocket().use {socket ->
                    vpn.bindSocket(socket);socket.connect(InetAddress.getByName(DnsVpnService.DNS4),53);socket.soTimeout=20000
                    val first=question("queue-load-0.invalid.",Type.A);socket.send(DatagramPacket(first,first.size))
                    assertTrue(loadEntered.get().await(30,java.util.concurrent.TimeUnit.SECONDS))
                    repeat(100){i ->val q=question("queue-load-${i+1}.invalid.",Type.A);socket.send(DatagramPacket(q,q.size))}
                    val reply=DatagramPacket(ByteArray(4096),4096);socket.receive(reply)
                    assertEquals(Rcode.SERVFAIL,Message(reply.data.copyOf(reply.length)).rcode)
                }
            }finally{udpGate.countDown();loadGate.set(null)}
            assertEquals(Stage.ACTIVE,DnsVpnService.state.value.stage)
            println("UDP_OVERLOAD_RETURNS_SERVFAIL_WITHOUT_VPN_FAILURE")
            withTimeout(30000){while(udp(DnsVpnService.DNS4,"recovery.example.org.",Type.A).rcode!=Rcode.NOERROR)delay(100)}
            // UDP connect selects a source/route without sending internet traffic.
            DatagramSocket().use{socket ->socket.connect(InetAddress.getByName("192.0.2.99"),443)
                assertNotEquals(DnsVpnService.CLIENT4,socket.localAddress.hostAddress)
                println("NON_DNS_SOURCE ${socket.localAddress.hostAddress}")
            }
            app.store.rule("doubleclick.net",false,true)
            withTimeout(5000){while(app.engine.blocked("doubleclick.net"))delay(50)}
            assertEquals(Rcode.NOERROR,udp(DnsVpnService.DNS4,"doubleclick.net.",Type.A).rcode)
            app.store.rule("doubleclick.net",false,false)
            unavailable.set(true)
            for(index in 0 until 8){
                if(DnsVpnService.state.value.stage==Stage.ERROR)break
                try {assertEquals(Rcode.SERVFAIL,udp(DnsVpnService.DNS4,"timeout-$index.invalid.",Type.A).rcode)}
                catch(e:SocketTimeoutException){if(DnsVpnService.state.value.stage!=Stage.ERROR)throw e else break}
            }
            if(DnsVpnService.state.value.stage!=Stage.ERROR)DatagramSocket().use { socket ->
                vpn.bindSocket(socket)
                val query=question("timeout-final.invalid.",Type.A)
                socket.send(DatagramPacket(query,query.size,InetAddress.getByName(DnsVpnService.DNS4),53))
            }
            val failed=withTimeout(30000){DnsVpnService.state.first{it.stage==Stage.ERROR}}
            assertTrue(failed.message,failed.message.contains("getrennt"))
            withTimeout(30000){while(cm.allNetworks.any{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true})delay(100)}
            println("CONTROLLED_FAILURE_VPN_RELEASED")
            // Rebuild without bypass protection: resolver host routes must disappear.
            unavailable.set(false);app.store.maximal(false);app.store.dnsGuard(false)
            context.startForegroundService(Intent(context,DnsVpnService::class.java).setAction(DnsVpnService.START))
            val standard=withTimeout(120000){DnsVpnService.state.first{it.stage==Stage.ACTIVE}}
            assertEquals(Stage.ACTIVE,standard.stage)
            withTimeout(30000){
                while(true){
                    val standardVpn=cm.allNetworks.firstOrNull{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}
                    val standardRoutes=standardVpn?.let{cm.getLinkProperties(it)?.routes}
                    if(standardRoutes!=null){
                        assertFalse(standardRoutes.toString(),standardRoutes.any{it.destination.toString()=="8.8.8.8/32"})
                        break
                    }
                    delay(100)
                }
            }
            println("STANDARD_MODE_RESOLVER_ROUTES_REMOVED")
        }catch(e:Exception){
            println("PACKET_FAILURE received=${received.get()} state=${DnsVpnService.state.value}")
            throw e
        }finally {
            loadGate.getAndSet(null)?.countDown()
            context.startForegroundService(Intent(context,DnsVpnService::class.java).setAction(DnsVpnService.STOP))
            withTimeout(30000){DnsVpnService.state.first{it.stage==Stage.OFF || it.stage==Stage.ERROR}}
            app.instrumentationUpstream=null
            app.instrumentationPacketObserver=null
            app.store.maximal(before.maximal);app.store.dnsGuard(before.dnsGuard)
            app.store.enabled(before.enabled)
            if("doubleclick.net" !in before.allow)app.store.rule("doubleclick.net",false,false)
        }
    }
    private fun question(name:String,type:Int)=Message.newQuery(Record.newRecord(Name(name),type,DClass.IN)).toWire()
    private fun udp(address:String,name:String,type:Int):Message {
        val bytes=question(name,type)
        return DatagramSocket().use{socket ->
            checkNotNull(tunnelNetwork).bindSocket(socket)
            socket.soTimeout=20000;socket.connect(InetAddress.getByName(address),53)
            println("UDP_ROUTE destination=$address source=${socket.localAddress.hostAddress} port=${socket.localPort}")
            socket.send(DatagramPacket(bytes,bytes.size,InetAddress.getByName(address),53))
            val receive=DatagramPacket(ByteArray(4096),4096);socket.receive(receive)
            Message(receive.data.copyOf(receive.length))
        }
    }
    private fun tcp(address:String,name:String,type:Int):Message=Socket().use{socket ->
        checkNotNull(tunnelNetwork).bindSocket(socket)
        socket.connect(InetSocketAddress(InetAddress.getByName(address),53),20000);socket.soTimeout=20000
        val bytes=question(name,type);val out=DataOutputStream(socket.getOutputStream());out.writeShort(bytes.size);out.write(bytes);out.flush()
        val input=DataInputStream(socket.getInputStream());val response=ByteArray(input.readUnsignedShort());input.readFully(response);Message(response)
    }
}
