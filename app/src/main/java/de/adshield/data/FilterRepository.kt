package de.adshield.data

import android.content.Context
import android.util.AtomicFile
import de.adshield.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONObject
import java.io.*
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val FILTER_URL="https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
data class FilterInfo(val count:Int=0,val updated:Long=0,val digest:String="",val bundled:Boolean=true,val updating:Boolean=false)
class FilterRepository(private val context:Context,private val store:AppStore,private val http:OkHttpClient=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build(),private val additionalText:(()->String)?=null,private val maximumText:(()->String)?=null,private val bundledText:(()->String)?=null){
    private enum class Kind(val asset:String,val url:String){BASE("hosts",FILTER_URL),MAX("hagezi-ultimate.txt",MaximalRules.URL),MORE("oisd-big.txt",OisdRules.URL)}
    private data class Parsed(val names:Set<String>,val hash:String)
    private data class Source(val kind:Kind,val open:()->InputStream)
    private val file=AtomicFile(File(context.filesDir,"filters-v3.zip"))
    private val previous=AtomicFile(File(context.filesDir,"filters-v2.current"))
    private val legacy=AtomicFile(File(context.filesDir,"hosts.current"))
    private val lock=Mutex()
    private var maximumCount=0;private var additionalCount=0
    private var trustedHashes:Map<Kind,String> = emptyMap()
    private var cacheTrusted=false
    val info=MutableStateFlow(FilterInfo())
    val domains=MutableStateFlow<Set<String>>(emptySet())
    val combined=MutableStateFlow<Set<String>>(emptySet())
    private fun bundled(kind:Kind):InputStream {
        val custom=when(kind){Kind.BASE->bundledText;Kind.MAX->maximumText;Kind.MORE->additionalText}
        return custom?.invoke()?.byteInputStream(Charsets.UTF_8)?:context.assets.open(kind.asset)
    }
    private fun parse(kind:Kind,input:InputStream,check:()->Unit):Parsed {
        val digest=MessageDigest.getInstance("SHA-256")
        val reader=DigestInputStream(LimitedInputStream(input,HostsParser.MAX_BYTES.toLong()),digest).reader(Charsets.UTF_8)
        val names=when(kind){Kind.BASE->HostsParser.parse(reader,check=check);Kind.MAX->MaximalRules.parse(reader,check=check);Kind.MORE->OisdRules.parse(reader,check=check)}
        return Parsed(names,digest.digest().joinToString(""){"%02x".format(it)})
    }
    private fun publish(parsed:Map<Kind,Parsed>,bundled:Boolean,updated:Long){
        val b=parsed.getValue(Kind.BASE);val m=parsed.getValue(Kind.MAX);val o=parsed.getValue(Kind.MORE)
        val fingerprint=Kind.entries.joinToString("\n"){it.name+":"+parsed.getValue(it).hash}.toByteArray(Charsets.UTF_8)
        val digest=MessageDigest.getInstance("SHA-256").digest(fingerprint).joinToString(""){"%02x".format(it)}
        trustedHashes=parsed.mapValues{it.value.hash}
        combined.value=MaximalRules.effective(b.names,m.names,true,o.names)
        domains.value=b.names;maximumCount=m.names.size;additionalCount=o.names.size
        info.value=FilterInfo(b.names.size,updated,digest,bundled)
    }
    private fun loadZip(check:()->Unit):Map<Kind,Parsed> = file.openRead().use { raw ->
        require(raw.channel.size()<=3L*HostsParser.MAX_BYTES+65536){"Filterarchiv zu groß"}
        ZipInputStream(raw.buffered()).use { zip ->
            val result=HashMap<Kind,Parsed>()
            while(true){
                val entry=zip.nextEntry?:break
                val kind=Kind.entries.singleOrNull{it.asset==entry.name}
                require(kind!=null && !entry.isDirectory && kind !in result){"Ungültiges Filterarchiv"}
                result[kind]=parse(kind,zip,check);zip.closeEntry()
            }
            require(result.size==Kind.entries.size){"Unvollständiges Filterarchiv"};result
        }
    }
    private fun saveZip(sources:List<Source>,check:()->Unit){
        check()
        val out=file.startWrite()
        try {
            val nonClosing=object:FilterOutputStream(out){
                // FilterOutputStream's default bulk write loops through write(Int) on Android.
                // Preserve compressed blocks as bulk writes while AtomicFile retains ownership.
                override fun write(bytes:ByteArray,offset:Int,length:Int){out.write(bytes,offset,length)}
                override fun close(){flush()}
            }
            ZipOutputStream(nonClosing).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                for(source in sources){
                    check();zip.putNextEntry(ZipEntry(source.kind.asset))
                    source.open().use { input ->
                        val limited=LimitedInputStream(input,HostsParser.MAX_BYTES.toLong());val buffer=ByteArray(8192)
                        while(true){check();val n=limited.read(buffer);if(n<0)break;zip.write(buffer,0,n)}
                    };zip.closeEntry()
                }
            }
            check();file.finishWrite(out)
        }catch(e:Exception){file.failWrite(out);throw e}
    }
    suspend fun load()=lock.withLock {withContext(Dispatchers.IO){
        val ctx=currentCoroutineContext();val checkpoint={ctx.ensureActive()}
        cacheTrusted=false
        try {val parsed=loadZip(checkpoint);cacheTrusted=true;publish(parsed,false,file.baseFile.lastModified());return@withContext}
        catch(e:CancellationException){throw e}catch(_:Exception){}
        val old=try {previous.openRead().use{JSONObject(LimitedInputStream(it,3L*HostsParser.MAX_BYTES+65536).bufferedReader().readText())}}
            catch(e:CancellationException){throw e}catch(_:Exception){null}
        if(old!=null){
            try {
                val values=mapOf(Kind.BASE to old.getString("base"),Kind.MAX to old.getString("maximum"),Kind.MORE to old.optString("oisd").takeIf{it.isNotEmpty()})
                val sources=Kind.entries.map{k ->Source(k){values[k]?.byteInputStream(Charsets.UTF_8)?:bundled(k)}}
                val parsed=sources.associate{s ->s.kind to s.open().use{parse(s.kind,it,checkpoint)}}
                val time=previous.baseFile.lastModified()
                try{saveZip(sources,checkpoint);cacheTrusted=true;file.baseFile.setLastModified(time)}
                catch(e:CancellationException){throw e}catch(_:Exception){cacheTrusted=false}
                publish(parsed,false,time);return@withContext
            }catch(e:CancellationException){throw e}catch(_:Exception){}
        }
        val sources=Kind.entries.map{k ->Source(k){if(k==Kind.BASE)runCatching{legacy.openRead()}.getOrNull()?:bundled(k) else bundled(k)}}
        try {
            val parsed=sources.associate{s ->s.kind to s.open().use{parse(s.kind,it,checkpoint)}}
            publish(parsed,true,0)
        }catch(e:CancellationException){throw e}catch(_:Exception){
            val parsed=Kind.entries.associate{k -> k to bundled(k).use{parse(k,it,checkpoint)}}
            publish(parsed,true,0)
        }
    }}
    private suspend fun download(kind:Kind,target:File)=suspendCancellableCoroutine<String>{continuation ->
        val call=http.newCall(Request.Builder().url(kind.url).build())
        continuation.invokeOnCancellation{call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(e)}
            override fun onResponse(call:Call,response:Response){
                try {
                    val digest=MessageDigest.getInstance("SHA-256")
                    response.use {
                        check(it.isSuccessful){"HTTP ${it.code}"}
                        val body=checkNotNull(it.body);check(body.contentLength()<=HostsParser.MAX_BYTES){"Download zu groß"}
                        target.outputStream().use { out ->
                            val input=LimitedInputStream(body.byteStream(),HostsParser.MAX_BYTES.toLong());val buffer=ByteArray(8192)
                            while(true){if(!continuation.isActive)throw CancellationException();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n);out.write(buffer,0,n)}
                        }
                    }
                    if(continuation.isActive)continuation.resume(digest.digest().joinToString(""){"%02x".format(it)})
                }catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}
            }
        })
    }
    private suspend fun saveError(message:String){
        try{store.updateError(message)}catch(e:CancellationException){throw e}catch(_:Exception){/* Filter data stays usable even if settings storage fails. */}
    }
    suspend fun update():Boolean=lock.withLock {withContext(Dispatchers.IO){
        val ctx=currentCoroutineContext();val checkpoint={ctx.ensureActive()}
        val stage=File(context.cacheDir,"filter-stage-${java.util.UUID.randomUUID()}")
        info.value=info.value.copy(updating=true)
        try {
            check(stage.mkdirs()){ "Temporärer Filterspeicher nicht verfügbar" }
            val downloaded=Kind.entries.associateWith{k ->val f=File(stage,k.asset);val hash=download(k,f);f to hash}
            val sources=Kind.entries.map{k ->Source(k){downloaded.getValue(k).first.inputStream()}}
            if(downloaded.all{(k,value)->trustedHashes[k]==value.second}){
                checkpoint()
                if(!cacheTrusted || !file.baseFile.isFile){saveZip(sources,checkpoint);cacheTrusted=true}
                else file.baseFile.setLastModified(System.currentTimeMillis())
                info.value=info.value.copy(bundled=false,updated=file.baseFile.lastModified())
                saveError("");return@withContext true
            }
            // Validate changed sources first so a bad new download fails without reparsing known files.
            val ordered=sources.sortedBy{if(trustedHashes[it.kind]==downloaded.getValue(it.kind).second)1 else 0}
            val parsed=ordered.associate{s ->s.kind to s.open().use{parse(s.kind,it,checkpoint)}}
            require(parsed.getValue(Kind.BASE).names.size>=domains.value.size/2 && parsed.getValue(Kind.MAX).names.size>=maximumCount/2 && parsed.getValue(Kind.MORE).names.size>=additionalCount/2){"Ungewöhnlich kleine Filter"}
            saveZip(sources,checkpoint);cacheTrusted=true;publish(parsed,false,file.baseFile.lastModified());saveError("");true
        }catch(e:CancellationException){throw e}
        catch(e:Exception){saveError("Filterupdate fehlgeschlagen. Bisherige Listen bleiben aktiv. Bitte Verbindung und freien Speicher prüfen.");false}
        finally{stage.deleteRecursively();info.value=info.value.copy(updating=false)}
    }}
}
