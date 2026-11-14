package de.adshield.data

import de.adshield.core.Outcome
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Bounded queue plus short batches: no coroutine or SQLite transaction per DNS packet.
 * Backpressure is applied on DNS worker threads, never on the UI or TUN reader.
 */
class StatsRecorder(private val dao:StatsDao,scope:CoroutineScope){
    private data class Event(val day:String,val outcome:Outcome,val generation:Long)
    private data class Counts(var total:Long=0,var blocked:Long=0,var allowed:Long=0,var errors:Long=0)
    private val channel=Channel<Event>(4096)
    private val generation=AtomicLong()
    private val writing=Mutex()
    val error=MutableStateFlow("")
    init {scope.launch(Dispatchers.IO){
        try {
            for(first in channel){
                delay(250)
                val counts=HashMap<Pair<Long,String>,Counts>()
                fun add(event:Event){
                    val count=counts.getOrPut(event.generation to event.day){Counts()};count.total++
                    when(event.outcome){Outcome.BLOCKED->count.blocked++;Outcome.ALLOWED->count.allowed++;Outcome.ERROR->count.errors++}
                }
                add(first)
                var received=1
                while(received<4096){val event=channel.tryReceive().getOrNull()?:break;add(event);received++}
                writing.withLock {
                    for((key,value)in counts)if(key.first==generation.get())dao.addCounts(key.second,value.total,value.blocked,value.allowed,value.errors)
                }
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){error.value="Statistiken konnten nicht gespeichert werden; die angezeigten Zähler sind unvollständig.";channel.close(e)}
    }}
    suspend fun clear(){writing.withLock{generation.incrementAndGet();dao.clear()}}
    fun record(day:String,outcome:Outcome){
        if(error.value.isNotEmpty())return
        val event=Event(day,outcome,generation.get())
        if(!channel.trySend(event).isSuccess){runBlocking{runCatching{channel.send(event)}}}
    }
}
