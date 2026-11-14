package de.adshield.vpn

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.*
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import de.adshield.*
import de.adshield.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.xbill.DNS.*
import org.xbill.DNS.Record
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.DatagramSocket
import java.time.LocalDate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class Stage { OFF, STARTING, ACTIVE, PAUSED, ERROR }
data class ProtectionState(val stage:Stage=Stage.OFF,val message:String="Schutz deaktiviert",val alwaysOn:Boolean=false,val lockdown:Boolean=false)
class DnsVpnService:VpnService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lifecycle=Mutex()
    private val app get()=application as AdShieldApp
    @Volatile private var tunnel:ParcelFileDescriptor?=null
    private var inputStream:FileInputStream?=null
    private var reader:Job?=null;private var ticker:Job?=null
    @Volatile private var upstream:ManagedUpstream?=null
    @Volatile private var engine:DnsEngine?=null
    private var tcp:TcpDnsServer?=null
    private var pool:ThreadPoolExecutor?=null
    @Volatile private var healthJob:Job?=null
    @Volatile private var guardActive=false
    @Volatile private var paused=false
    @Volatile private var ending=false
    private val failures=AtomicInteger()
    private val pruneDay=java.util.concurrent.atomic.AtomicReference("")
    private val connectivity by lazy {getSystemService(ConnectivityManager::class.java)}
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network){upstream?.networkChanged();engine?.clearCache();verifyNetwork()}
        override fun onLost(network:Network){upstream?.networkChanged();engine?.clearCache();verifyNetwork()}
    }
    override fun onCreate(){
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"DNS-Schutz",NotificationManager.IMPORTANCE_LOW))
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),callback)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        ending=false
        try {foreground("DNS-Schutz wird vorbereitet")}
        catch(e:Exception){state.value=ProtectionState(Stage.ERROR,"Android konnte den VPN-Vordergrunddienst nicht starten: ${e.javaClass.simpleName}");stopSelf();return START_NOT_STICKY}
        scope.launch {
            lifecycle.withLock {
                try {
                    app.ready.await()
                    when(intent?.action){
                        STOP -> {app.store.enabled(false);if(isAlwaysOn){paused=true;startTunnel()}else{ending=true;release();publish(Stage.OFF,"Schutz deaktiviert");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}
                        else -> {
                            if(intent?.action==START)app.store.enabled(true)
                            paused=!app.store.settings.first().enabled
                            if(tunnel==null || intent?.action==REBUILD || intent?.action==START)startTunnel()
                            else publish(if(paused)Stage.PAUSED else Stage.ACTIVE,if(paused)"Lokaler Filter pausiert · Quad9-DNS bleibt aktiv" else "Schutz aktiv")
                        }
                    }
                }catch(e:CancellationException){throw e}
                catch(e:Exception){fail("VPN konnte nicht gestartet werden: ${e.message?:e.javaClass.simpleName}")}
            }
        }
        return START_STICKY
    }
    private suspend fun startTunnel(){
        check(!isLockdownEnabled){"Bitte in Androids VPN-Einstellungen „Verbindungen ohne VPN blockieren“ ausschalten. DNS-only-VPNs können mit dieser Option den normalen Datenverkehr nicht erhalten."}
        publish(Stage.STARTING,"DNS-Verbindung wird geprüft …")
        release();ending=false;failures.set(0)
        val settings=app.store.settings.first()
        val resolver=if(BuildConfig.DEBUG)app.instrumentationUpstream?:Quad9(this) else Quad9(this)
        upstream=resolver
        val dns=DnsEngine(app.engine,resolver);engine=dns
        val probe=Record.newRecord(Name("example.com."),Type.A,DClass.IN)
        val probeResult=dns.resolve(Message.newQuery(probe).toWire(),filtering=false)
        check(!probeResult.transportFailure){DnsFailureMessages.describe(probeResult.failureCause)}
        val probeResponse=Message(probeResult.wire)
        check(probeResponse.rcode==Rcode.NOERROR && probeResponse.getSection(Section.ANSWER).isNotEmpty()){"Quad9 hat den DNS-Verbindungstest nicht beantwortet (DNS-Code ${probeResponse.rcode})."}
        check(app.filters.domains.value.isNotEmpty()){"Keine gültige Filterliste"}
        app.filters.domains.value.firstOrNull{!Domains.matches(it,settings.allow)}?.let { domain ->
            val blockedProbe=Message.newQuery(Record.newRecord(Name(domain+"."),Type.A,DClass.IN))
            check(dns.resolve(blockedProbe.toWire()).outcome==Outcome.BLOCKED){"Lokaler Filtertest fehlgeschlagen"}
        }
        val builder=Builder().setSession("AdShield · DNS-Schutz").setMtu(1500).setBlocking(true)
            .addAddress(CLIENT4,32).addAddress(CLIENT6,128)
            .addRoute(DNS4,32).addRoute(DNS6,128).addDnsServer(DNS4).addDnsServer(DNS6)
            .setConfigureIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
        guardActive=settings.maximal && settings.dnsGuard && !paused
        if(guardActive){
            DnsBypassPolicy.ipv4.forEach { builder.addRoute(it,32) }
            DnsBypassPolicy.ipv6.forEach { builder.addRoute(it,128) }
        }
        settings.excluded.filter{it!=packageName}.forEach {pkg -> runCatching{builder.addDisallowedApplication(pkg)}}
        // No default route. At dedicated captured resolver IPs, only port 53 is handled;
        // encrypted DNS ports are deliberately dropped, never decrypted or forwarded.
        val fd=checkNotNull(builder.establish()){"VPN-Berechtigung fehlt oder wurde widerrufen"};tunnel=fd
        val output=FileOutputStream(fd.fileDescriptor)
        val writeLock=Any()
        fun emit(bytes:ByteArray){synchronized(writeLock){if(tunnel===fd)output.write(bytes)}}
        val tcpWake=Channel<Unit>(Channel.CONFLATED)
        val fragments=FragmentReassembler()
        val endpoint=TcpDnsServer(resolve={query -> resolve(dns,query,true).wire},emit=::emit);tcp=endpoint
        val workers=ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,ArrayBlockingQueue(128),ThreadPoolExecutor.DiscardPolicy());pool=workers
        val input=FileInputStream(fd.fileDescriptor);inputStream=input
        reader=scope.launch {
            try {
                input.use { input ->
                    val buffer=ByteArray(65535)
                    while(isActive && tunnel===fd){
                        val count = input.read(buffer)
                        if (count < 0) { break }
                        if(BuildConfig.DEBUG)app.instrumentationPacketObserver?.invoke(buffer.copyOf(count))
                        val wire = fragments.accept(buffer.copyOf(count)) ?: continue
                        val packet = IpPacket.parse(wire) ?: continue
                        if (packet.destinationPort != 53 || !validDestination(packet)) { continue }
                        workers.execute {
                            try {
                                if (tunnel !== fd) { return@execute }
                                if (packet.protocol == 17) {
                                    val query = packet.transport.copyOfRange(8, packet.transport.size)
                                    emit(packet.udpResponse(resolve(dns, query, false).wire))
                                } else {
                                    endpoint.accept(packet)
                                    tcpWake.trySend(Unit)
                                }
                            } catch (_: Exception) {
                                if (tunnel === fd && !ending) {
                                    scope.launch {
                                        lifecycle.withLock {
                                            if(tunnel===fd && !ending)fail("Fehler bei der DNS-Paketverarbeitung. VPN wurde getrennt.")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if(tunnel===fd && !ending)lifecycle.withLock{if(tunnel===fd && !ending)fail("Die VPN-Schnittstelle wurde geschlossen.")}
            }catch(_:Exception){if(tunnel===fd && !ending)lifecycle.withLock{if(tunnel===fd && !ending)fail("VPN-Verbindung unterbrochen. Bitte erneut aktivieren.")}}
        }
        ticker=scope.launch {
            try {
                for (signal in tcpWake) {
                    while (isActive && endpoint.hasSessions()) {
                        delay(1000)
                        endpoint.tick()
                    }
                }
            } finally { tcpWake.close();fragments.clear() }
        }
        awaitDnsRoutes()
        publish(if(paused)Stage.PAUSED else Stage.ACTIVE,if(paused)"Lokaler Filter pausiert · Quad9-DNS bleibt aktiv" else "Schutz aktiv")
    }
    /** LinkProperties can arrive before netd installs its routes. A UDP connect sends
     * no application payload and verifies the actual kernel-selected source address.
     */
    private suspend fun awaitDnsRoutes() {
        val result = withTimeoutOrNull(15000) {
            while (isActive) {
                val candidates = connectivity.allNetworks.filter { network ->
                    connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                        connectivity.getLinkProperties(network)?.dnsServers?.any { it.hostAddress == DNS4 } == true
                }
                val ready = candidates.any { network ->
                    listOf(DNS4 to CLIENT4, DNS6 to CLIENT6).all { (destination, source) ->
                        runCatching {
                            DatagramSocket().use { socket ->
                                network.bindSocket(socket)
                                socket.connect(InetAddress.getByName(destination), 53)
                                socket.localAddress == InetAddress.getByName(source)
                            }
                        }.getOrDefault(false)
                    }
                }
                if (ready) { return@withTimeoutOrNull true }
                delay(100)
            }
            false
        }
        check(result == true) { "Android hat die DNS-Routen nicht rechtzeitig eingerichtet. Bitte erneut aktivieren." }
    }
    @Synchronized private fun verifyNetwork() {
        if (tunnel == null || ending) { return }
        healthJob?.cancel()
        healthJob = scope.launch {
            delay(600) // Coalesce bursts during Wi-Fi/mobile handovers. No idle polling.
            lifecycle.withLock {
                val dns = engine ?: return@withLock
                val fd = tunnel ?: return@withLock
                publish(Stage.STARTING, "Netzwechsel · DNS wird erneut geprüft …")
                val query = Message.newQuery(Record.newRecord(Name("example.com."), Type.A, DClass.IN))
                var result = dns.resolve(query.toWire(), filtering = false)
                ensureActive()
                if(result.transportFailure){
                    delay(700)
                    result=dns.resolve(query.toWire(),filtering=false)
                }
                val response = Message(result.wire)
                ensureActive()
                if (tunnel !== fd) { return@withLock }
                if (isLockdownEnabled) {
                    fail("Android-VPN-Lockdown ist eingeschaltet. Bitte „Verbindungen ohne VPN blockieren“ für DNS-only ausschalten.")
                } else if (response.rcode == Rcode.NOERROR && response.getSection(Section.ANSWER).isNotEmpty()) {
                    failures.set(0)
                    publish(if (paused) Stage.PAUSED else Stage.ACTIVE,
                        if (paused) "Lokaler Filter pausiert · Quad9-DNS bleibt aktiv" else "Schutz aktiv")
                } else {
                    fail("DNS nach dem Netzwechsel nicht erreichbar. VPN wurde getrennt. " + DnsFailureMessages.describe(result.failureCause))
                }
            }
        }
    }
    private fun validDestination(p:IpPacket):Boolean {
        val expected=InetAddress.getByName(if(p.version==4)DNS4 else DNS6).address
        return p.destination.contentEquals(expected) || (guardActive && DnsBypassPolicy.contains(p.destination))
    }
    private fun resolve(dns:DnsEngine,query:ByteArray,tcp:Boolean):DnsResult {
        val result=dns.resolve(query,tcp,!paused)
        if(engine !== dns)return result // Late replies from a replaced tunnel cannot affect its successor.
        val date=LocalDate.now()
        val day=date.toString()
        if(pruneDay.getAndSet(day)!=day)app.scope.launch{app.stats.prune(date.minusDays(90).toString())}
        app.recorder.record(day,result.outcome)
        if(result.transportFailure){
            // During a handover the bounded health check owns the teardown decision.
            if(healthJob?.isActive==true)return result
            if(failures.incrementAndGet()>=5)scope.launch{lifecycle.withLock{if(tunnel!=null && engine===dns)fail("DNS antwortet wiederholt nicht. VPN wurde getrennt; bei Android-Lockdown kann Internet gesperrt bleiben.")}}
        }else failures.set(0)
        return result
    }
    private fun publish(stage:Stage,text:String){state.value=ProtectionState(stage,text,isAlwaysOn,isLockdownEnabled);foreground(text)}
    private fun fail(text:String){
        ending=true;release()
        state.value=ProtectionState(Stage.ERROR,text,isAlwaysOn,isLockdownEnabled)
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    private fun release(){
        healthJob?.cancel();healthJob=null
        val old=tunnel;tunnel=null
        runCatching{inputStream?.close()};inputStream=null
        runCatching{old?.close()};reader?.cancel();reader=null;ticker?.cancel();ticker=null
        upstream?.close();upstream=null;engine=null;tcp?.close();tcp=null;pool?.shutdownNow();pool=null
    }
    private fun foreground(text:String){
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,DnsVpnService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_shield).setContentTitle("AdShield").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(0,"Pausieren / ausschalten",stop).build()
        if(Build.VERSION.SDK_INT>=34)startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) else startForeground(1,notification)
    }
    override fun onRevoke(){ending=true;release();state.value=ProtectionState(Stage.ERROR,"VPN-Berechtigung widerrufen oder durch ein anderes VPN ersetzt.");stopSelf()}
    override fun onDestroy(){
        ending=true;release();runCatching{connectivity.unregisterNetworkCallback(callback)};scope.cancel()
        if(state.value.stage!=Stage.ERROR)state.value=ProtectionState()
        super.onDestroy()
    }
    companion object {
        const val START="de.adshield.START";const val STOP="de.adshield.STOP";const val REBUILD="de.adshield.REBUILD"
        const val DNS4="10.111.0.2";const val CLIENT4="10.111.0.1"
        const val DNS6="fdad:51e1:d::2";const val CLIENT6="fdad:51e1:d::1"
        private const val CHANNEL="dns-protection"
        val state=MutableStateFlow(ProtectionState())
    }
}
