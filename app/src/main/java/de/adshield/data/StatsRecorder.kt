package de.adshield.data

import de.adshield.core.Outcome
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesced counters, never domain names. No waiting for SQLite on DNS workers. */
class StatsRecorder(private val dao:StatsDao,scope:CoroutineScope){
    private data class Counts(var total:Long=0,var blocked:Long=0,var allowed:Long=0,var errors:Long=0)
    private val wake=Channel<Unit>(Channel.CONFLATED)
    private val pendingLock=Any()
    private val pending=HashMap<String,Counts>()
    private var generation=0L
    private val writing=Mutex()
    @Volatile private var failed=false
    val error=MutableStateFlow("")
    init {scope.launch(Dispatchers.IO){
        try {
            for(signal in wake){
                delay(250)
                val batch=synchronized(pendingLock){val copy=pending.toMap();pending.clear();generation to copy}
                writing.withLock {
                    if(batch.first==synchronized(pendingLock){generation})for((day,c)in batch.second)dao.addCounts(day,c.total,c.blocked,c.allowed,c.errors)
                }
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){failed=true;error.value="Statistiken konnten nicht gespeichert werden; die Zähler sind unvollständig. Der DNS-Schutz bleibt unabhängig davon aktiv.";wake.close()}
    }}
    suspend fun clear(){writing.withLock{
        synchronized(pendingLock){generation++;pending.clear()}
        dao.clear()
    }}
    fun record(day:String,outcome:Outcome){
        if(failed)return
        val notify=synchronized(pendingLock){
            if(day !in pending && pending.size>=8){error.value="Zu viele Tageswechsel während einer Speicherverzögerung; einige Zähler fehlen.";return}
            val first=pending.isEmpty()
            val c=pending.getOrPut(day){Counts()};c.total++
            when(outcome){Outcome.BLOCKED->c.blocked++;Outcome.ALLOWED->c.allowed++;Outcome.ERROR->c.errors++}
            first
        }
        // One wake-up covers the entire pending batch. The writer clears it under
        // the same lock; the first event in the next batch schedules another wake.
        if(notify)wake.trySend(Unit)
    }
}
