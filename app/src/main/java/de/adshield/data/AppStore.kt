package de.adshield.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

private val Context.preferences by preferencesDataStore("settings")
data class Settings(val allow:Set<String> = emptySet(), val deny:Set<String> = emptySet(), val excluded:Set<String> = emptySet(), val enabled:Boolean=false, val updateHours:Long=24, val theme:String="system", val updateError:String="", val maximal:Boolean=true)
class AppStore(private val context:Context) {
    private val allow=stringSetPreferencesKey("allow"); private val deny=stringSetPreferencesKey("deny")
    private val excluded=stringSetPreferencesKey("excluded"); private val enabled=booleanPreferencesKey("enabled")
    private val hours=longPreferencesKey("updateHours"); private val theme=stringPreferencesKey("theme")
    private val error=stringPreferencesKey("updateError")
    private val maximal=booleanPreferencesKey("maximal")
    suspend fun maximal(value:Boolean){context.preferences.edit{it[maximal]=value}}
    val settings=context.preferences.data.map { p -> Settings(p[allow]?:emptySet(),p[deny]?:emptySet(),p[excluded]?:emptySet(),p[enabled]?:false,p[hours]?:24,p[theme]?:"system",p[error]?:"",p[maximal]?:true) }
    suspend fun enabled(value:Boolean){context.preferences.edit{it[enabled]=value}}
    suspend fun rule(domain:String,blocked:Boolean,add:Boolean) {context.preferences.edit{p ->
        val key=if(blocked)deny else allow;val old=p[key]?:emptySet();p[key]=if(add)old+domain else old-domain
    }}
    suspend fun exclude(pkg:String,value:Boolean){context.preferences.edit{p -> val old=p[excluded]?:emptySet();p[excluded]=if(value)old+pkg else old-pkg}}
    suspend fun interval(value:Long){require(value in setOf(0L,24L,48L,168L));context.preferences.edit{it[hours]=value}}
    suspend fun theme(value:String){require(value in setOf("system","light","dark"));context.preferences.edit{it[theme]=value}}
    suspend fun updateError(value:String){context.preferences.edit{it[error]=value}}
}
@Entity(tableName="daily")
data class DailyStats(@PrimaryKey val day:String,val total:Long=0,val blocked:Long=0,val allowed:Long=0,val errors:Long=0)
@Dao interface StatsDao {
    @Query("SELECT * FROM daily WHERE day >= :since ORDER BY day DESC") fun observe(since:String):Flow<List<DailyStats>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun ensureDay(row:DailyStats):Long
    @Query("UPDATE daily SET total=total+:total,blocked=blocked+:blocked,allowed=allowed+:allowed,errors=errors+:errors WHERE day=:day")
    suspend fun accumulate(day:String,total:Long,blocked:Long,allowed:Long,errors:Long)
    @Transaction suspend fun addCounts(day:String,total:Long,blocked:Long,allowed:Long,errors:Long){
        ensureDay(DailyStats(day));accumulate(day,total,blocked,allowed,errors)
    }
    suspend fun increment(day:String,blocked:Int,allowed:Int,errors:Int){addCounts(day,1,blocked.toLong(),allowed.toLong(),errors.toLong())}
    @Query("DELETE FROM daily WHERE day < :before") suspend fun prune(before:String)
    @Query("DELETE FROM daily") suspend fun clear()
}
@Database(entities=[DailyStats::class],version=1,exportSchema=false)
abstract class StatsDatabase:RoomDatabase(){abstract fun stats():StatsDao}
