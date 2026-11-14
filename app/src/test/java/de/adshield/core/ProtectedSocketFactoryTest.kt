package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class ProtectedSocketFactoryTest {
    @Test fun nativeSocketIsCreatedBeforeProtectionAndBeforeConnect(){
        var calls=0
        val factory=ProtectedSocketFactory{socket ->calls++;assertTrue(socket.isBound);assertFalse(socket.isConnected);assertTrue(socket.localPort>0);true}
        factory.createSocket().use{assertTrue(it.isBound);assertFalse(it.isConnected)}
        assertEquals(1,calls)
    }
    @Test fun rejectedProtectionClosesSocket(){
        var seen:Socket?=null
        val factory=ProtectedSocketFactory{seen=it;false}
        assertThrows(SocketProtectionException::class.java){factory.createSocket()}
        assertTrue(checkNotNull(seen).isClosed)
    }
    @Test fun allConnectingOverloadsProtectBeforeConnectAndExchangeBytes(){
        val loopback=InetAddress.getByName("127.0.0.1")
        var calls=0
        val factory=ProtectedSocketFactory{socket ->assertTrue(socket.isBound);assertFalse(socket.isConnected);calls++;true}
        ServerSocket(0,4,loopback).use{server ->
            server.soTimeout=3000
            val openers=listOf<()->Socket>(
                {factory.createSocket("127.0.0.1",server.localPort)},
                {factory.createSocket(loopback,server.localPort)},
                {factory.createSocket("127.0.0.1",server.localPort,loopback,0)},
                {factory.createSocket(loopback,server.localPort,loopback,0)}
            )
            openers.forEach{open ->open().use{client ->
                client.soTimeout=3000;client.getOutputStream().write(67)
                server.accept().use{peer ->peer.soTimeout=3000;assertEquals(67,peer.getInputStream().read());peer.getOutputStream().write(68)}
                assertEquals(68,client.getInputStream().read())
            }}
        }
        assertEquals(4,calls)
    }
}
