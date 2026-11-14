package de.adshield.core

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Bounded admission and age; stale requests never start a new upstream operation. */
class DnsWorkQueue(workers:Int=4,capacity:Int=64,private val maxWaitMillis:Long=2000,label:String="UDP",private val clock:()->Long={System.nanoTime()/1000000}) {
    private val ids=AtomicInteger()
    private val executor=ThreadPoolExecutor(workers,workers,30,TimeUnit.SECONDS,ArrayBlockingQueue(capacity),{task ->
        Thread(task,"AdShield-$label-${ids.incrementAndGet()}").apply{isDaemon=true}
    },ThreadPoolExecutor.AbortPolicy()).apply{allowCoreThreadTimeOut(true)}
    fun submit(work:()->Unit,expired:()->Unit){
        val queued=clock()
        try {executor.execute{if(clock()-queued>=maxWaitMillis)expired() else work()}}
        catch(_:RejectedExecutionException){expired()}
    }
    fun shutdownNow(){executor.shutdownNow()}
}
