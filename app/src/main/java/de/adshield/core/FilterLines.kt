package de.adshield.core

import java.io.Reader

internal object FilterLines {
    fun each(reader:Reader,check:()->Unit,accept:(String,Int)->Unit){
        val input=reader.buffered();var characters=0L;var count=0
        while(true){
            val line=input.readLine()?:break
            characters+=line.length+1
            require(characters<=HostsParser.MAX_BYTES+1L){"Filterdatei ist zu groß"}
            if(count%1024==0)check()
            accept(line,count++)
        }
        check()
    }
}
