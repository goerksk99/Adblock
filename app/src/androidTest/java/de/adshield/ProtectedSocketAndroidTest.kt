package de.adshield

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.adshield.core.ProtectedSocketFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class ProtectedSocketAndroidTest {
    @Before fun authorizeVpnOwner(){
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set de.adshield ACTIVATE_VPN allow")).bufferedReader().use{it.readText()}
        assertNull(VpnService.prepare(ApplicationProvider.getApplicationContext<Context>()))
    }
    @Test fun socketCanBeProtectedBeforeVpnEstablishment(){
        val service=VpnService()
        Socket().use{socket ->
            val original=runCatching{service.protect(socket)}.getOrDefault(false)
            println("LEGACY_UNBOUND_SOCKET_PROTECTED=$original bound=${socket.isBound}")
        }
        val factory=ProtectedSocketFactory{socket ->service.protect(socket)}
        factory.createSocket().use{socket ->
            assertTrue(socket.isBound);assertFalse(socket.isConnected)
            assertTrue("Android must accept the real native descriptor before connect",service.protect(socket))
            println("BOUND_SOCKET_PROTECTED_BEFORE_VPN=true")
        }
    }
    @Test fun protectedSocketConnectsAndTransfersDataOnAndroid(){
        val service=VpnService();val loopback=InetAddress.getByName("127.0.0.1")
        val factory=ProtectedSocketFactory{socket ->assertTrue(socket.isBound);assertFalse(socket.isConnected);service.protect(socket)}
        ServerSocket(0,1,loopback).use{server ->
            server.soTimeout=10000
            factory.createSocket(loopback,server.localPort,loopback,0).use{client ->
                client.soTimeout=10000;client.getOutputStream().write(67)
                server.accept().use{peer ->peer.soTimeout=10000;assertEquals(67,peer.getInputStream().read());peer.getOutputStream().write(68)}
                assertEquals(68,client.getInputStream().read())
            }
        }
    }
    @Test fun protectedWildcardSocketAlsoConnectsOverIpv6(){
        val service=VpnService();val loopback=InetAddress.getByName("::1")
        val factory=ProtectedSocketFactory{socket ->service.protect(socket)}
        ServerSocket(0,1,loopback).use{server ->
            server.soTimeout=10000
            factory.createSocket().use{client ->
                client.connect(java.net.InetSocketAddress(loopback,server.localPort),10000)
                client.soTimeout=10000;client.getOutputStream().write(69)
                server.accept().use{peer ->peer.soTimeout=10000;assertEquals(69,peer.getInputStream().read());peer.getOutputStream().write(70)}
                assertEquals(70,client.getInputStream().read())
            }
        }
    }
}
