package de.adshield.data

import android.content.Context
import android.util.AtomicFile
import de.adshield.core.HostsParser
import de.adshield.core.MaximalRules
import de.adshield.core.OisdRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

const val FILTER_URL="https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
data class FilterInfo(val count:Int=0,val updated:Long=0,val digest:String="",val bundled:Boolean=true,val updating:Boolean=false)
class FilterRepository(private val context:Context,private val store:AppStore,private val http:OkHttpClient=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build(),private val additionalText:()->String={context.assets.open("oisd-big.txt").bufferedReader().use{it.readText()}},private val maximumText:()->String={context.assets.open("hagezi-ultimate.txt").bufferedReader().use{it.readText()}},private val bundledText:()->String={context.assets.open("hosts").bufferedReader().use{it.readText()}}) {
    private val file=AtomicFile(File(context.filesDir,"filters-v2.current"))
    private val legacy=AtomicFile(File(context.filesDir,"hosts.current"))
    private val lock=Mutex()
    private var additionalCount=0
    val info=MutableStateFlow(FilterInfo())
    val domains=MutableStateFlow<Set<String>>(emptySet())
    val maximum=MutableStateFlow<Set<String>>(emptySet())
    val combined=MutableStateFlow<Set<String>>(emptySet())
    suspend fun load()=withContext(Dispatchers.IO) {
        val restored=runCatching {
            val json=JSONObject(file.openRead().bufferedReader().use{it.readText()})
            val base=json.getString("base");val strong=json.getString("maximum")
            val more=json.optString("oisd").takeIf{it.isNotEmpty()}?:additionalText()
            val b=HostsParser.parse(base);val m=MaximalRules.parse(strong);val o=OisdRules.parse(more)
            publish(base,strong,more,b,m,o,false)
        }.isSuccess
        if(!restored){
            val base=runCatching{legacy.openRead().bufferedReader().use{it.readText()}.also{HostsParser.parse(it)}}.getOrNull()?:bundledText()
            val strong=maximumText()
            val more=additionalText()
            publish(base,strong,more,HostsParser.parse(base),MaximalRules.parse(strong),OisdRules.parse(more),true)
        }
    }
    private fun publish(base:String,strong:String,more:String,b:Set<String>,m:Set<String>,o:Set<String>,bundled:Boolean) {
        combined.value=MaximalRules.effective(b,m,true,o);maximum.value=m;domains.value=b;additionalCount=o.size
        val digest=MessageDigest.getInstance("SHA-256").digest((base+"\n"+strong+"\n"+more).toByteArray()).joinToString(""){"%02x".format(it)}
        info.value=FilterInfo(b.size,if(bundled)0 else file.baseFile.lastModified(),digest,bundled)
    }
    private fun download(url:String):String = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful){"HTTP ${response.code}"}
        val body=checkNotNull(response.body);check(body.contentLength()<=HostsParser.MAX_BYTES){"Download zu groß"}
        val source=body.source();val buffer=okio.Buffer();var total=0L
        while(true){val read=source.read(buffer,8192);if(read<0)break;total+=read;check(total<=HostsParser.MAX_BYTES){"Download zu groß"}}
        buffer.readUtf8()
    }
    suspend fun update():Boolean=lock.withLock {withContext(Dispatchers.IO) {
        info.value=info.value.copy(updating=true)
        try {
            val base=download(FILTER_URL);val b=HostsParser.parse(base)
            val strong=download(MaximalRules.URL);val m=MaximalRules.parse(strong)
            val more=download(OisdRules.URL);val o=OisdRules.parse(more)
            check(b.size>=domains.value.size/2 && m.size>=maximum.value.size/2 && o.size>=additionalCount/2){"Ungewöhnlich kleine Filter"}
            val data=JSONObject().put("base",base).put("maximum",strong).put("oisd",more).toString().toByteArray(Charsets.UTF_8)
            val stream=file.startWrite()
            try {stream.write(data);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
            publish(base,strong,more,b,m,o,false);store.updateError("");true
        }catch(e:Exception){store.updateError("Filterupdate fehlgeschlagen: ${e.message?:"Netzwerkfehler"}. Bisherige Listen bleiben aktiv.");false}
        finally{info.value=info.value.copy(updating=false)}
    }}
}
