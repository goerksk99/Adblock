package de.adshield.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.adshield.AdShieldApp
import de.adshield.core.Domains
import de.adshield.data.Settings
import de.adshield.vpn.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class AppEntry(val name:String,val pkg:String,val icon:Bitmap)
class ShieldModel(application:Application):AndroidViewModel(application){
    private val app=application as AdShieldApp
    val settings=app.store.settings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),Settings())
    val protection=DnsVpnService.state
    val filter=combine(app.filters.info,app.filters.domains,app.filters.combined,app.store.settings){info,base,maximum,s -> info.copy(count=(if(s.maximal)maximum else base).size)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),de.adshield.data.FilterInfo())
    val statsError=app.recorder.error
    val stats=app.stats.observe(LocalDate.now().minusDays(90).toString()).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val apps=MutableStateFlow<List<AppEntry>>(emptyList())
    val notice=MutableStateFlow("")
    val diagnostic=MutableStateFlow("Noch nicht geprüft. Der Test prüft den AdShield-DNS-Pfad auf diesem Gerät.")
    val diagnosing=MutableStateFlow(false)
    fun diagnose(){
        if(diagnosing.value)return
        diagnosing.value=true
        viewModelScope.launch(Dispatchers.IO){
            try {
                if(protection.value.stage!=Stage.ACTIVE){diagnostic.value="Bitte zuerst den Schutz aktivieren. Ein pausierter oder ausgeschalteter Filter kann nicht geprüft werden.";return@launch}
                app.ready.await()
                val blocked=app.filters.domains.value.firstOrNull{app.engine.blocked(it)}
                val allowed=listOf("example.com","example.org","example.net").firstOrNull{!app.engine.blocked(it)}
                if(blocked==null || allowed==null){diagnostic.value="Keine geeigneten Testdomains: Bitte deine Domain-Ausnahmen prüfen.";return@launch}
                val cm=app.getSystemService(android.net.ConnectivityManager::class.java)
                val network=cm.allNetworks.firstOrNull{n -> cm.getNetworkCapabilities(n)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)==true && cm.getLinkProperties(n)?.dnsServers?.any{it.hostAddress==DnsVpnService.DNS4}==true}
                    ?:error("AdShield-DNS-Route nicht gefunden. Bitte Schutz erneut aktivieren.")
                val result=de.adshield.core.ProtectionProbe.run(blocked,allowed){query ->
                    java.net.DatagramSocket().use {socket ->
                        network.bindSocket(socket);socket.soTimeout=6000
                        socket.connect(java.net.InetAddress.getByName(DnsVpnService.DNS4),53)
                        socket.send(java.net.DatagramPacket(query,query.size))
                        val response=java.net.DatagramPacket(ByteArray(4096),4096);socket.receive(response)
                        response.data.copyOf(response.length)
                    }
                }
                diagnostic.value=when(result){
                    de.adshield.core.ProtectionProbe.Result.HEALTHY->"Erfolgreich: Lokale Sperre und normale DNS-Auflösung funktionieren. Browser mit eigenem DNS können trotzdem abweichen."
                    de.adshield.core.ProtectionProbe.Result.BLOCKING_FAILED->"Die Testdomain wurde nicht gesperrt. Bitte Modus und Ausnahmen prüfen und den Schutz neu starten."
                    de.adshield.core.ProtectionProbe.Result.RESOLUTION_FAILED->"Die Sperre funktioniert, normale Auflösung aber nicht. Internetverbindung und Quad9-Erreichbarkeit prüfen."
                    else->"Die DNS-Antwort war ungültig. Bitte den Schutz erneut starten."
                }
                val privateDns=android.provider.Settings.Global.getString(app.contentResolver,"private_dns_mode")
                if(privateDns=="hostname")diagnostic.value+=" Android Private DNS ist strikt eingestellt. Bei Browserproblemen dessen Einstellung oder den DNS-Umgehungsschutz prüfen."
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){diagnostic.value=if(e is java.net.SocketTimeoutException)"Der DNS-Test hat keine Antwort erhalten. Verbindung prüfen; bei festem Browser-DNS den zusätzlichen Umgehungsschutz testweise ausschalten." else "Der Schutztest konnte nicht abgeschlossen werden. Bitte VPN und Verbindung prüfen und erneut testen."}
            finally{diagnosing.value=false}
        }
    }
    @Volatile private var appsLoaded=false
    fun loadApps(){
        if(appsLoaded)return
        appsLoaded=true
        viewModelScope.launch(Dispatchers.IO){
        try {
        val pm=app.packageManager
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps.value=pm.queryIntentActivities(intent,PackageManager.MATCH_ALL).map{it.activityInfo.applicationInfo}
            .distinctBy{it.packageName}.filter{it.packageName!=app.packageName}.mapNotNull{
                runCatching{AppEntry(pm.getApplicationLabel(it).toString(),it.packageName,pm.getApplicationIcon(it).toBitmap(80,80))}.getOrNull()
            }.sortedBy{it.name.lowercase()}
        }catch(e:CancellationException){throw e}
        catch(e:Exception){appsLoaded=false;notice.value="Die App-Liste konnte nicht geladen werden. Bitte den Tab erneut öffnen."}
    }}
    fun service(action:String){runCatching{app.startForegroundService(Intent(app,DnsVpnService::class.java).setAction(action))}.onFailure{notice.value="Android konnte den Dienst nicht starten: ${it.message}"}}
    fun rule(value:String,blocked:Boolean,add:Boolean){viewModelScope.launch{
        try {app.store.rule(Domains.normalize(value),blocked,add)}catch(e:Exception){notice.value=e.message?:"Ungültige Domain"}
    }}
    fun exclude(pkg:String,value:Boolean){viewModelScope.launch{
        app.store.exclude(pkg,value)
        if(protection.value.stage in setOf(Stage.ACTIVE,Stage.PAUSED,Stage.STARTING))service(DnsVpnService.REBUILD)
    }}
    private var updateJob:Job?=null
    fun update(){if(updateJob?.isActive==true)return;updateJob=viewModelScope.launch{
        try{app.ready.await();if(!app.filters.update())notice.value="Filterupdate fehlgeschlagen. Die bisherigen Listen bleiben aktiv; Verbindung und freien Speicher prüfen."}
        catch(e:CancellationException){throw e}
        catch(_:Exception){notice.value="Filter konnten nicht aktualisiert werden. Bitte Verbindung und Speicher prüfen."}
    }}
    private fun rebuildIfActive(){if(protection.value.stage in setOf(Stage.ACTIVE,Stage.PAUSED,Stage.STARTING))service(DnsVpnService.REBUILD)}
    fun maximal(value:Boolean){viewModelScope.launch{app.store.maximal(value);rebuildIfActive()}}
    fun dnsGuard(value:Boolean){viewModelScope.launch{app.store.dnsGuard(value);rebuildIfActive()}}
    fun interval(value:Long){viewModelScope.launch{app.store.interval(value)}}
    fun theme(value:String){viewModelScope.launch{app.store.theme(value)}}
    fun clearStats(){viewModelScope.launch{app.recorder.clear()}}
    fun denied(){notice.value="VPN-Berechtigung nicht erteilt. Schutz bleibt deaktiviert."}
}
