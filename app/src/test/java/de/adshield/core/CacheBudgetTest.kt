package de.adshield.core

import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.net.InetAddress

class CacheBudgetTest {
    private fun query(name:String)=Message.newQuery(Record.newRecord(Name("$name."),Type.A,DClass.IN)).toWire()
    @Test fun byteBudgetEvictsAndClearReleasesEntries(){
        var calls=0
        val e=DnsEngine(FilterEngine(),{raw ->calls++;Message(raw).apply{header.setFlag(Flags.QR.toInt());addRecord(ARecord(question.name,DClass.IN,60,InetAddress.getByName("192.0.2.1")),Section.ANSWER)}.toWire()},cacheBudgetBytes=300)
        e.resolve(query("one.example"));e.resolve(query("two.example"));assertEquals(2,calls)
        e.resolve(query("two.example"));assertEquals(2,calls)
        e.resolve(query("one.example"));assertEquals(3,calls)
        e.clearCache();e.resolve(query("one.example"));assertEquals(4,calls)
    }
    @Test fun zeroBudgetNeverStoresButStillResolves(){
        var calls=0;val e=DnsEngine(FilterEngine(),{raw ->calls++;Message(raw).apply{header.setFlag(Flags.QR.toInt());addRecord(ARecord(question.name,DClass.IN,60,InetAddress.getByName("192.0.2.1")),Section.ANSWER)}.toWire()},cacheBudgetBytes=0)
        repeat(3){assertEquals(Outcome.ALLOWED,e.resolve(query("one.example")).outcome)};assertEquals(3,calls)
    }
}
