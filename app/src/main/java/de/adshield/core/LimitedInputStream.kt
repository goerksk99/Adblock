package de.adshield.core

import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException

/** Count actual bytes before decoding or inflating further into application buffers. */
class LimitedInputStream(input:InputStream,private val limit:Long):FilterInputStream(input){
    private var count=0L
    override fun read():Int {val b=`in`.read();if(b>=0)account(1);return b}
    override fun read(b:ByteArray,off:Int,len:Int):Int {
        val n=`in`.read(b,off,minOf(len.toLong(),(limit-count+1).coerceAtLeast(1)).toInt())
        if(n>0)account(n);return n
    }
    override fun skip(n:Long):Long {var left=n;val buffer=ByteArray(4096);while(left>0){val read=read(buffer,0,minOf(left,buffer.size.toLong()).toInt());if(read<0)break;left-=read};return n-left}
    private fun account(n:Int){count+=n;if(count>limit)throw IOException("Filterdatei überschreitet das Größenlimit")}
    override fun markSupported()=false
    override fun reset():Unit=throw IOException("reset wird nicht unterstützt")
}
