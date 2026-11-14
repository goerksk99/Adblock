package de.adshield.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import javax.net.SocketFactory

class SocketProtectionException : SocketException("Android konnte den DNS-Socket nicht vom VPN ausnehmen.")

/** Android creates Socket's native descriptor lazily. Bind before protect, then connect.
 * The protector must never see an uncreated descriptor or already-connected socket.
 */
class ProtectedSocketFactory(private val protect:(Socket)->Boolean):SocketFactory() {
    private fun newSocket(local:InetSocketAddress?=null):Socket {
        val socket=Socket()
        try {
            socket.bind(local)
            if(!protect(socket))throw SocketProtectionException()
            return socket
        }catch(error:Exception){runCatching{socket.close()};throw error}
    }
    private fun connect(socket:Socket,remote:InetSocketAddress):Socket {
        try {socket.connect(remote);return socket}
        catch(error:Exception){runCatching{socket.close()};throw error}
    }
    override fun createSocket():Socket=newSocket()
    override fun createSocket(host:String,port:Int):Socket=connect(newSocket(),InetSocketAddress(host,port))
    override fun createSocket(host:InetAddress,port:Int):Socket=connect(newSocket(),InetSocketAddress(host,port))
    override fun createSocket(host:String,port:Int,local:InetAddress,localPort:Int):Socket=connect(newSocket(InetSocketAddress(local,localPort)),InetSocketAddress(host,port))
    override fun createSocket(host:InetAddress,port:Int,local:InetAddress,localPort:Int):Socket=connect(newSocket(InetSocketAddress(local,localPort)),InetSocketAddress(host,port))
}
