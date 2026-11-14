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
    init {viewModelScope.launch(Dispatchers.IO){
        val pm=app.packageManager
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps.value=pm.queryIntentActivities(intent,PackageManager.MATCH_ALL).map{it.activityInfo.applicationInfo}
            .distinctBy{it.packageName}.filter{it.packageName!=app.packageName}.mapNotNull{
                runCatching{AppEntry(pm.getApplicationLabel(it).toString(),it.packageName,pm.getApplicationIcon(it).toBitmap(80,80))}.getOrNull()
            }.sortedBy{it.name.lowercase()}
    }}
    fun service(action:String){runCatching{app.startForegroundService(Intent(app,DnsVpnService::class.java).setAction(action))}.onFailure{notice.value="Android konnte den Dienst nicht starten: ${it.message}"}}
    fun rule(value:String,blocked:Boolean,add:Boolean){viewModelScope.launch{
        try {app.store.rule(Domains.normalize(value),blocked,add)}catch(e:Exception){notice.value=e.message?:"Ungültige Domain"}
    }}
    fun exclude(pkg:String,value:Boolean){viewModelScope.launch{
        app.store.exclude(pkg,value)
        if(protection.value.stage in setOf(Stage.ACTIVE,Stage.PAUSED,Stage.STARTING))service(DnsVpnService.REBUILD)
    }}
    fun update(){viewModelScope.launch{app.ready.await();app.filters.update()}}
    private fun rebuildIfActive(){if(protection.value.stage in setOf(Stage.ACTIVE,Stage.PAUSED,Stage.STARTING))service(DnsVpnService.REBUILD)}
    fun maximal(value:Boolean){viewModelScope.launch{app.store.maximal(value);rebuildIfActive()}}
    fun dnsGuard(value:Boolean){viewModelScope.launch{app.store.dnsGuard(value);rebuildIfActive()}}
    fun interval(value:Long){viewModelScope.launch{app.store.interval(value)}}
    fun theme(value:String){viewModelScope.launch{app.store.theme(value)}}
    fun clearStats(){viewModelScope.launch{app.recorder.clear()}}
    fun denied(){notice.value="VPN-Berechtigung nicht erteilt. Schutz bleibt deaktiviert."}
}
