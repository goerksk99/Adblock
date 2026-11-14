package de.adshield

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.adshield.core.*
import de.adshield.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class FilterLifecycleTest {
    private val app get()=ApplicationProvider.getApplicationContext<Context>() as AdShieldApp
    private class Fixture(context:Context){
        val root=File(context.cacheDir,"snapshot-fixture-${System.nanoTime()}").apply{mkdirs()}
        val files=File(root,"files").apply{mkdirs()};val cache=File(root,"cache").apply{mkdirs()}
        val context=object:ContextWrapper(context){override fun getFilesDir()=files;override fun getCacheDir()=cache}
        val base="# StevenBlack/hosts\n"+(0 until 1000).joinToString("\n"){"0.0.0.0 base-$it.example"}
        val maximum="# Title: HaGeZi's Multi ULTIMATE\n# Syntax: Domains\n# Number of entries: 50000\n"+(0 until 50000).joinToString("\n"){"max-$it.example"}
        val more="# Title: oisd big\n# Syntax: Domains (wildcards)\n# Entries: 50000\n"+(0 until 50000).joinToString("\n"){"*.more-$it.example"}
        fun repo(store:AppStore,http:OkHttpClient=OkHttpClient())=FilterRepository(context,store,http,additionalText={more},maximumText={maximum},bundledText={base})
        fun body(url:String)=when(url){MaximalRules.URL->maximum;OisdRules.URL->more;else->base}
    }
    @Test fun legacyTwoSourceSnapshotMigratesAndZipRestores()=runBlocking {
        val f=Fixture(app)
        try {
            File(f.files,"filters-v2.current").writeText(JSONObject().put("base",f.base).put("maximum",f.maximum).toString())
            val repo=f.repo(app.store);repo.load()
            assertTrue(File(f.files,"filters-v3.zip").isFile)
            assertTrue("more-49999.example" in repo.combined.value)
            val old=repo.info.value.digest
            val restored=f.repo(app.store);restored.load()
            assertEquals(old,restored.info.value.digest);assertEquals(repo.combined.value,restored.combined.value)
            assertTrue(File(f.files,"filters-v3.zip").length()<(f.base.length+f.maximum.length+f.more.length))
        }finally{f.root.deleteRecursively()}
    }
    @Test fun cancelledHttpCannotCommitAndCleansStaging()=runBlocking {
        val f=Fixture(app);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val http=OkHttpClient.Builder().addInterceptor{chain ->
            entered.countDown();check(release.await(20,TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body(f.body(chain.request().url.toString()).toResponseBody("text/plain".toMediaType())).build()
        }.build()
        try {
            val repo=f.repo(app.store,http);repo.load();val old=repo.info.value.digest
            val job=launch(Dispatchers.IO){repo.update()}
            try{assertTrue(entered.await(10,TimeUnit.SECONDS));job.cancelAndJoin()}finally{release.countDown()}
            delay(300)
            assertEquals(old,repo.info.value.digest);assertFalse(repo.info.value.updating)
            assertFalse(File(f.files,"filters-v3.zip").exists())
            assertTrue(f.cache.listFiles().orEmpty().none{it.name.startsWith("filter-stage-")})
        }finally{release.countDown();http.dispatcher.cancelAll();f.root.deleteRecursively()}
    }
    @Test fun identicalTrustedUpdatesDoNotRewriteArchiveOrReplaceRuleSet()=runBlocking {
        val f=Fixture(app)
        val http=OkHttpClient.Builder().addInterceptor{chain ->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").body(f.body(chain.request().url.toString()).toResponseBody("text/plain".toMediaType())).build()}.build()
        try {
            val repo=f.repo(app.store,http);repo.load();assertTrue(repo.update())
            val archive=File(f.files,"filters-v3.zip");val bytes=archive.readBytes();val names=repo.combined.value
            assertTrue(repo.update());assertArrayEquals(bytes,archive.readBytes());assertSame(names,repo.combined.value)
        }finally{http.dispatcher.cancelAll();f.root.deleteRecursively()}
    }
    @Test fun invalidZipEntriesNeverExtractAndFallbackToTrustedAssets()=runBlocking {
        val f=Fixture(app)
        try {
            ZipOutputStream(File(f.files,"filters-v3.zip").outputStream()).use{z ->z.putNextEntry(ZipEntry("../escaped"));z.write(byteArrayOf(1));z.closeEntry()}
            val repo=f.repo(app.store);repo.load()
            assertTrue(repo.info.value.bundled);assertTrue("more-49999.example" in repo.combined.value)
            assertFalse(File(f.root,"escaped").exists())
        }finally{f.root.deleteRecursively()}
    }
    @Test fun unavailableStagingStorageReturnsFailureInsteadOfCrashing()=runBlocking {
        val f=Fixture(app)
        try {
            val blocked=File(f.root,"not-a-directory").apply{writeText("x")}
            val context=object:ContextWrapper(f.context){override fun getCacheDir()=blocked}
            val repo=FilterRepository(context,app.store,additionalText={f.more},maximumText={f.maximum},bundledText={f.base})
            repo.load();val old=repo.info.value.digest
            assertFalse(repo.update());assertEquals(old,repo.info.value.digest);assertFalse(repo.info.value.updating)
        }finally{app.store.updateError("");f.root.deleteRecursively()}
    }
}
