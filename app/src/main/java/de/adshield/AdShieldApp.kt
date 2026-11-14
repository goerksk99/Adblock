package de.adshield

import android.app.Application
import androidx.room.Room
import de.adshield.core.*
import de.adshield.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AdShieldApp:Application() {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    lateinit var store:AppStore;private set
    lateinit var filters:FilterRepository;private set
    lateinit var stats:StatsDao;private set
    lateinit var recorder:StatsRecorder;private set
    // In-process instrumentation seam only; the release service never reads this field.
    @Volatile internal var instrumentationUpstream:de.adshield.vpn.ManagedUpstream?=null
    @Volatile internal var instrumentationPacketObserver:((ByteArray)->Unit)?=null
    val engine=FilterEngine()
    val ready=CompletableDeferred<Unit>()
    override fun onCreate(){
        super.onCreate()
        store=AppStore(this);filters=FilterRepository(this,store)
        stats=Room.databaseBuilder(this,StatsDatabase::class.java,"statistics.db").build().stats()
        recorder=StatsRecorder(stats,scope)
        scope.launch {
            try {
                filters.load()
                val settings=store.settings.first()
                engine.replace(RuleSet(if(settings.maximal)filters.combined.value else filters.domains.value,settings.allow,settings.deny,settings.maximal && settings.dnsGuard))
                ready.complete(Unit)
            } catch(e:Exception){ready.completeExceptionally(e)}
        }
        scope.launch {combine(filters.domains,filters.combined,store.settings){hosts,maximum,s -> RuleSet(if(s.maximal)maximum else hosts,s.allow,s.deny,s.maximal && s.dnsGuard)}.collect{engine.replace(it)}}
        scope.launch {store.settings.map{it.updateHours}.distinctUntilChanged().collect{FilterWorker.schedule(this@AdShieldApp,it)}}
        scope.launch(Dispatchers.IO){stats.prune(java.time.LocalDate.now().minusDays(90).toString())}
    }
}
