package de.adshield

import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.adshield.data.*
import de.adshield.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun statisticsAggregateWithoutDoubleCountingAndPersist()=runBlocking {
        val name="test-statistics-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,StatsDatabase::class.java,name).build()
        try {
            val dao=db.stats();dao.increment("2026-11-11",0,1,0);dao.increment("2026-11-12",1,0,0);dao.increment("2026-11-12",0,0,1)
            var rows=dao.observe("2026-11-01").first()
            assertEquals(2,rows.size);assertEquals(DailyStats("2026-11-12",2,1,0,1),rows.first())
            db.close();db=Room.databaseBuilder(context,StatsDatabase::class.java,name).build()
            rows=db.stats().observe("2026-11-12").first();assertEquals(1,rows.size);assertEquals(2L,rows.single().total)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun settingsPersistRulesAndAppExclusions()=runBlocking {
        val store=(context as AdShieldApp).store
        store.rule("test.adshield.invalid",false,true);store.exclude("de.adshield.test.invalid",true)
        val previous=store.settings.first().maximal;store.maximal(!previous);assertEquals(!previous,store.settings.first().maximal);store.maximal(previous)
        val read=store.settings.first();assertTrue("test.adshield.invalid" in read.allow);assertTrue("de.adshield.test.invalid" in read.excluded)
        store.rule("test.adshield.invalid",false,false);store.exclude("de.adshield.test.invalid",false)
    }
    @Test fun invalidAndEmptyUpdatesRetainLastWorkingFile()=runBlocking {
        val store=(context as AdShieldApp).store
        val isolatedDir=File(context.cacheDir,"filter-test-${System.nanoTime()}").apply{mkdirs()}
        val isolated=object:android.content.ContextWrapper(context){override fun getFilesDir()=isolatedDir}
        val bundled="# StevenBlack/hosts\n"+(0 until 2000).joinToString("\n"){"0.0.0.0 ad-$it.example"}
        var maximumBody=context.assets.open("hagezi-ultimate.txt").bufferedReader().use{it.readText()}
        var body=bundled
        var code=200
        val http=OkHttpClient.Builder().addInterceptor{chain ->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test response").body((if(chain.request().url.toString()==MaximalRules.URL)maximumBody else body).toResponseBody("text/plain".toMediaType())).build()}.build()
        val repo=FilterRepository(isolated,store,http){bundled};repo.load()
        assertTrue(repo.update());val old=repo.info.value
        maximumBody="broken";assertFalse(repo.update());assertEquals(old.digest,repo.info.value.digest)
        maximumBody=context.assets.open("hagezi-ultimate.txt").bufferedReader().use{it.readText()}
        body="";assertFalse(repo.update());assertEquals(old.digest,repo.info.value.digest)
        body="<html>invalid</html>";assertFalse(repo.update());assertEquals(old.count,repo.info.value.count)
        code=503;assertFalse(repo.update());assertEquals(old.digest,repo.info.value.digest)
        val restored=FilterRepository(isolated,store,http){bundled};restored.load();assertEquals(old.digest,restored.info.value.digest)
        // Leave the test device with its original bundled filter and no update-error banner.
        isolatedDir.deleteRecursively();store.updateError("")
    }
    @Test fun bundledFilterIsUsableAndMatchesKnownAdDomain()=runBlocking {
        val app=context as AdShieldApp
        app.ready.await()
        assertTrue(app.filters.info.value.count>50000)
        val filter=RuleSet(app.filters.domains.value)
        assertTrue(filter.blocked("doubleclick.net"));assertFalse(filter.blocked("example.com"))
        val before=app.store.settings.first().maximal
        try {
            app.store.maximal(true);withTimeout(30000){while(!app.engine.blocked("freshmarketer.com"))delay(50)}
            app.store.maximal(false);withTimeout(30000){while(app.engine.blocked("freshmarketer.com"))delay(50)}
        } finally {app.store.maximal(before)}
    }
    @Test fun batchedStatisticsAndClearAreConsistent()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,StatsDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val recorder=StatsRecorder(db.stats(),scope)
            repeat(15){i ->recorder.record("2026-11-12",Outcome.entries[i%3])}
            val counts=withTimeout(30000){db.stats().observe("2026-11-12").first{it.sumOf{row->row.total}==15L}}
            assertEquals(DailyStats("2026-11-12",15,5,5,5),counts.single())
            repeat(10){recorder.record("2026-11-12",Outcome.BLOCKED)}
            recorder.clear();recorder.record("2026-11-12",Outcome.ALLOWED)
            val cleared=withTimeout(30000){db.stats().observe("2026-11-12").first{it.sumOf{row->row.total}==1L}}
            assertEquals(DailyStats("2026-11-12",1,0,1,0),cleared.single())
        }finally{scope.cancel();db.close()}
    }
}
