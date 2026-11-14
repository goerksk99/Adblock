package de.adshield.core

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class DnsBypassPolicyTest {
    @Test fun exactDedicatedAddressesOnly() {
        val all=DnsBypassPolicy.ipv4+DnsBypassPolicy.ipv6
        assertEquals(20,all.size)
        for(ip in all) assertTrue(ip,DnsBypassPolicy.contains(InetAddress.getByName(ip).address))
        for(ip in listOf("1.1.1.2","8.8.8.7","192.0.2.1","2001:db8::1","2001:4860:4860::8889")) assertFalse(ip,DnsBypassPolicy.contains(InetAddress.getByName(ip).address))
    }
    @Test fun resolverNamesAreOptionalAndAllowRulesWin() {
        val guarded=RuleSet(resolverGuard=true)
        val standard=RuleSet()
        for(name in DnsBypassPolicy.names){assertTrue(name,guarded.blocked(name));assertFalse(name,standard.blocked(name))}
        assertTrue(guarded.blocked("profile.dns.nextdns.io"))
        assertFalse(guarded.blocked("dns.google.example.org"))
        assertFalse(RuleSet(allow=setOf("dns.google"),resolverGuard=true).blocked("dns.google"))
        assertFalse(guarded.blocked("google.com"))
    }
    @Test fun udpRepliesPreserveCapturedResolverIdentity() {
        for(ip in DnsBypassPolicy.ipv4+DnsBypassPolicy.ipv6){
            val destination=InetAddress.getByName(ip).address;val version=if(destination.size==4)4 else 6
            val source=InetAddress.getByName(if(version==4)"10.111.0.1" else "fdad:51e1:d::1").address
            val udp=ByteArray(20).apply{Wire.put16(this,0,40000);Wire.put16(this,2,53);Wire.put16(this,4,size)}
            val query=checkNotNull(IpPacket.parse(IpPacket.encode(version,source,destination,17,udp)))
            val reply=checkNotNull(IpPacket.parse(query.udpResponse(ByteArray(12))))
            assertArrayEquals(ip,destination,reply.source);assertArrayEquals(source,reply.destination)
            assertEquals(53,reply.sourcePort);assertEquals(40000,reply.destinationPort)
        }
    }
}
