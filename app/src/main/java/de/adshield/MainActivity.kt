package de.adshield

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.adshield.data.*
import de.adshield.ui.*
import de.adshield.vpn.*
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

class MainActivity:ComponentActivity(){
    private val model:ShieldModel by viewModels()
    private val consent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){if(it.resultCode==RESULT_OK)startProtection() else model.denied()}
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            val dark=when(settings.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
            val colors=if(Build.VERSION.SDK_INT>=31){if(dark)dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)}else if(dark)darkColorScheme(primary=Color(0xff69dbc2)) else lightColorScheme(primary=Color(0xff006b58))
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars=!dark
                    isAppearanceLightNavigationBars=!dark
                }
            }
            MaterialTheme(colorScheme=colors){ShieldApp(model,settings,::activate){startActivity(Intent(Settings.ACTION_VPN_SETTINGS))}}
        }
    }
    private fun startProtection(){
        model.service(DnsVpnService.START)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    private fun activate(){
        val intent=VpnService.prepare(this)
        if(intent!=null)consent.launch(intent) else startProtection()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ShieldApp(model:ShieldModel,settings:de.adshield.data.Settings,activate:()->Unit,vpnSettings:()->Unit){
    var page by rememberSaveableState(0)
    var explain by remember {mutableStateOf(false)}
    var reset by remember {mutableStateOf(false)}
    val protection by model.protection.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val stats by model.stats.collectAsStateWithLifecycle()
    val statsError by model.statsError.collectAsStateWithLifecycle()
    val diagnostic by model.diagnostic.collectAsStateWithLifecycle()
    val diagnosing by model.diagnosing.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(notice){if(notice.isNotEmpty()){snackbar.showSnackbar(notice);model.notice.value=""}}
    LaunchedEffect(page){if(page==2)model.loadApps()}
    val titles=listOf("Übersicht","Statistiken","Apps","Ausnahmen","Einstellungen")
    val symbols=listOf(Icons.Default.Home,Icons.Default.Info,Icons.Default.List,Icons.Default.Check,Icons.Default.Settings)
    Scaffold(topBar={TopAppBar(title={Column{Text("AdShield",fontWeight=FontWeight.Bold);Text("Privater DNS-Schutz",style=MaterialTheme.typography.labelMedium)}})},
        snackbarHost={SnackbarHost(snackbar)},
        bottomBar={NavigationBar{titles.forEachIndexed{i,title -> NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(symbols[i],contentDescription=title)},label={Text(title,fontSize=10.sp,letterSpacing=0.sp,maxLines=1)},alwaysShowLabel=true)}}}){padding ->
        Box(Modifier.fillMaxSize().padding(padding)){
            when(page){
                0 -> Overview(protection,filter,stats,{
                    if(protection.stage in setOf(Stage.ACTIVE,Stage.STARTING))model.service(DnsVpnService.STOP) else explain=true
                })
                1 -> Statistics(stats,statsError,{reset=true})
                2 -> AppsScreen(apps,settings.excluded,model::exclude)
                3 -> RulesScreen(settings,model::rule)
                else -> SettingsScreen(settings,filter,model::update,model::interval,model::theme,model::maximal,model::dnsGuard,diagnostic,diagnosing,model::diagnose,vpnSettings)
            }
        }
    }
    if(explain)AlertDialog(onDismissRequest={explain=false},title={Text("Warum eine VPN-Freigabe?")},text={Text("AdShield nutzt Androids lokale VPN-Schnittstelle nur für DNS-Anfragen. Es gibt keinen entfernten VPN-Server und keine TLS-Entschlüsselung. Erlaubte Anfragen gehen verschlüsselt an Quad9. Quad9 sieht die angefragten Domains und deine IP-Adresse. Nur ein Android-VPN kann gleichzeitig aktiv sein. Fortfahren?")},confirmButton={TextButton(onClick={explain=false;activate()}){Text("Android-Freigabe öffnen")}},dismissButton={TextButton(onClick={explain=false}){Text("Abbrechen")}})
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Statistiken löschen?")},text={Text("Alle lokal gespeicherten Tageszähler werden gelöscht.")},confirmButton={TextButton(onClick={model.clearStats();reset=false}){Text("Löschen")}},dismissButton={TextButton(onClick={reset=false}){Text("Abbrechen")}})
}
// Save the selected page across rotation without persisting transient permission dialogs.
@Composable private fun rememberSaveableState(initial:Int)=androidx.compose.runtime.saveable.rememberSaveable{mutableIntStateOf(initial)}
@Composable private fun PageTitle(title:String,subtitle:String){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun InfoCard(title:String,body:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(body,style=MaterialTheme.typography.bodyMedium)}}}
@Composable private fun Metric(title:String,value:String,modifier:Modifier=Modifier){Card(modifier){Column(Modifier.padding(18.dp)){Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(title,style=MaterialTheme.typography.labelMedium)}}}
@Composable private fun Overview(state:ProtectionState,filter:FilterInfo,stats:List<DailyStats>,toggle:()->Unit){
    val today=stats.firstOrNull{it.day==LocalDate.now().toString()}?:DailyStats(LocalDate.now().toString())
    val active=state.stage==Stage.ACTIVE
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{PageTitle("Dein Internet. Ruhiger.","Werbe- und Trackingdomains lokal filtern.")}
        item{Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
            FilledTonalButton(onClick=toggle,enabled=state.stage!=Stage.STARTING,shape=CircleShape,modifier=Modifier.size(164.dp)){
                Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(if(active)Icons.Default.Check else Icons.Default.Close,contentDescription=null,modifier=Modifier.size(54.dp));Text(if(active)"Ausschalten" else if(state.stage==Stage.STARTING)"Verbinden …" else "Aktivieren")}
            }
            Text(state.message,style=MaterialTheme.typography.titleMedium,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if(state.stage==Stage.STARTING)LinearProgressIndicator(Modifier.fillMaxWidth())
        }}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("Blockierte Anfragen heute",today.blocked.toString(),Modifier.weight(1f));Metric("Blockierungsquote",percent(today.blocked,today.total),Modifier.weight(1f))}}
        item{InfoCard(if(filter.count==0)"Filter werden geladen …" else "${filter.count} Filterdomains",if(filter.bundled)"Aktive Domainregeln · Quellen und Schutzmodus unter Einstellungen" else "Letztes Update: ${date(filter.updated)}")}
        if(state.alwaysOn)item{InfoCard("Always-on VPN",if(state.lockdown)"Android blockiert Verbindungen ohne VPN. Ausnahmen und Fehler können die Verbindung sperren. Bei Problemen diese Android-Option deaktivieren." else "Der Ausschalter pausiert den lokalen Filter. Android hält die DNS-Verbindung zu Quad9 aktiv.")}
        item{InfoCard("Was der Schutz leisten kann","Bekannte Werbe-, Tracking- und Bedrohungsdomains werden reduziert. DNS-Filter entfernen keine Seitenelemente und blockieren YouTube-, Instagram- oder TikTok-Werbung nicht zuverlässig. Blockierte Anfragen sind keine gezählten Werbeanzeigen.")}
    }
}
@Composable private fun Statistics(stats:List<DailyStats>,error:String,clear:()->Unit){
    var days by remember{mutableIntStateOf(7)}
    val start=LocalDate.now().minusDays(days.toLong()-1)
    val selection=stats.filter{it.day>=start.toString()}
    val total=selection.sumOf{it.total};val blocked=selection.sumOf{it.blocked}
    val color=MaterialTheme.colorScheme.primary
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageTitle("Echte Anfragen", "Nur lokale Tageszähler · keine Domainhistorie")}
        if(error.isNotEmpty())item{Text(error,color=MaterialTheme.colorScheme.error)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(1,7,30).forEach{d -> FilterChip(selected=days==d,onClick={days=d},label={Text(if(d==1)"Heute" else "$d Tage")})}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("DNS-Anfragen",total.toString(),Modifier.weight(1f));Metric("Blockiert",blocked.toString(),Modifier.weight(1f))}}
        item{InfoCard("${percent(blocked,total)} blockiert","Erlaubt: ${selection.sumOf{it.allowed}} · Fehler: ${selection.sumOf{it.errors}}\nFehler zählen als Anfragen, aber weder als erlaubt noch als blockiert.")}
        item{Text("Blockierte Anfragen pro Tag",fontWeight=FontWeight.SemiBold)}
        item{
            val values=(0 until days).map{i ->stats.firstOrNull{it.day==start.plusDays(i.toLong()).toString()}?.blocked?:0L}
            val max=(values.maxOrNull()?:0).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(140.dp)){
                val width=size.width/values.size
                values.forEachIndexed{i,v ->val h=size.height*v.toFloat()/max;drawRect(color,androidx.compose.ui.geometry.Offset(i*width+2,size.height-h),androidx.compose.ui.geometry.Size((width-4).coerceAtLeast(1f),h))}
            }
            Text("${start} – ${LocalDate.now()}",style=MaterialTheme.typography.labelSmall)
        }
        if(selection.isEmpty())item{Text("Noch keine Anfragen erfasst. Aktiviere den Schutz und nutze dein Gerät.")}
        items(selection,key={it.day}){d ->ListItem(headlineContent={Text(d.day)},supportingContent={Text("${d.total} gesamt · ${d.allowed} erlaubt · ${d.errors} Fehler")},trailingContent={Text("${d.blocked} blockiert")})}
        item{TextButton(onClick=clear){Text("Statistiken löschen")}}
    }
}
@Composable private fun AppsScreen(apps:List<AppEntry>,excluded:Set<String>,toggle:(String,Boolean)->Unit){
    var query by remember{mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageTitle("Apps", "Schutz für alle Apps, außer deinen Ausnahmen.")}
        item{InfoCard("Sichtbare Apps","Hier erscheinen startbare Apps, die Android für AdShield sichtbar macht. Keine weitreichende App-Sichtbarkeitsberechtigung. Änderungen bauen das VPN kurz neu auf. Mit Android-VPN-Lockdown können ausgeschlossene Apps offline sein.")}
        item{OutlinedTextField(query,{query=it},label={Text("Apps durchsuchen")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        items(apps.filter{it.name.contains(query,true)||it.pkg.contains(query,true)},key={it.pkg}){app ->
            ListItem(headlineContent={Text(app.name)},supportingContent={Text(app.pkg,style=MaterialTheme.typography.labelSmall)},leadingContent={Image(app.icon.asImageBitmap(),contentDescription=null,modifier=Modifier.size(40.dp))},trailingContent={Switch(checked=app.pkg !in excluded,onCheckedChange={toggle(app.pkg,!it)})})
        }
        if(apps.isEmpty())item{Text("Keine sichtbaren startbaren Apps gefunden.")}
        if(excluded.isNotEmpty())item{Text("${excluded.size} gespeicherte Ausnahmen. Nicht mehr sichtbare Pakete können unten entfernt werden.")}
        items(excluded.filter{pkg ->apps.none{it.pkg==pkg}}){pkg ->ListItem(headlineContent={Text(pkg)},trailingContent={TextButton(onClick={toggle(pkg,false)}){Text("Entfernen")}})}
    }
}
@Composable private fun RulesScreen(settings:de.adshield.data.Settings,change:(String,Boolean,Boolean)->Unit){
    var blocked by remember{mutableStateOf(false)};var input by remember{mutableStateOf("")};var query by remember{mutableStateOf("")}
    val rules=if(blocked)settings.deny else settings.allow
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{PageTitle("Deine Domainregeln","Änderungen gelten unmittelbar für neue Anfragen.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!blocked,{blocked=false},label={Text("Erlauben")});FilterChip(blocked,{blocked=true},label={Text("Blockieren")})}}
        item{InfoCard("Domain und Subdomains","example.com gilt auch für login.example.com. Freigaben haben Vorrang vor allen lokalen Sperren. Eine Domainfreigabe ist keine komplette Website-Ausnahme. Quad9s Bedrohungssperren werden damit nicht aufgehoben; bereits im Browser gecachte Antworten können bis zum TTL-Ablauf weitergelten.")}
        item{OutlinedTextField(input,{input=it},label={Text("Domain, z. B. example.com")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Button(onClick={change(input,blocked,true);input=""},enabled=input.isNotBlank()){Icon(Icons.Default.Add,null);Text("Domain hinzufügen")}}
        item{OutlinedTextField(query,{query=it},label={Text("Regeln durchsuchen")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        items(rules.filter{it.contains(query,true)}.sorted(),key={it}){domain ->ListItem(headlineContent={Text(domain)},trailingContent={IconButton(onClick={change(domain,blocked,false)}){Icon(Icons.Default.Delete,"Regel entfernen")}})}
        if(rules.isEmpty())item{Text("Noch keine eigenen Regeln.")}
    }
}
@Composable private fun SettingsScreen(settings:de.adshield.data.Settings,filter:FilterInfo,update:()->Unit,interval:(Long)->Unit,theme:(String)->Unit,maximal:(Boolean)->Unit,dnsGuard:(Boolean)->Unit,diagnostic:String,diagnosing:Boolean,diagnose:()->Unit,vpnSettings:()->Unit){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageTitle("Einstellungen","Privat. Lokal. Ohne Konto.")}
        item{ListItem(headlineContent={Text("Maximaler Schutz")},supportingContent={Text("HaGeZi Ultimate + OISD Big + Trackingregeln und DNS-Alias-Prüfung. Kann App-Funktionen einschränken. Erlaubte Domains haben Vorrang.")},trailingContent={Switch(checked=settings.maximal,onCheckedChange=maximal)})}
        item{ListItem(headlineContent={Text("Bekannte DNS-Umgehungen einschränken")},supportingContent={Text("Im Maximalmodus: feste öffentliche DNS-Adressen filtern und bekannte verschlüsselte Resolver-Ziele sperren. Striktes Browser-DNS kann dann ausfallen; bei Bedarf hier ausschalten. Unbekannte Resolver bleiben möglich.")},trailingContent={Switch(checked=settings.dnsGuard,onCheckedChange=dnsGuard,enabled=settings.maximal)})}
        item{InfoCard("Schutztest auf diesem Gerät",diagnostic)}
        item{Button(onClick=diagnose,enabled=!diagnosing){Text(if(diagnosing)"DNS-Pfad wird geprüft …" else "Schutz jetzt prüfen")}}
        item{InfoCard("Filterquellen: StevenBlack + HaGeZi Ultimate + OISD Big","Standardmodus: StevenBlack. Maximalmodus: zusätzlich HaGeZi Ultimate, OISD Big und gezielte Tracking-Sperren. MIT/GPL-3.0 und Bedingungen einzelner Quelllisten. Quellen und Hinweise liegen in der App unter Lizenzen.\n${filter.count} eindeutige Domains\n${if(filter.bundled)"Vorinstallierte Stände: StevenBlack 08.11., HaGeZi/OISD 14.11.2026" else "Aktualisiert: "+date(filter.updated)}\nQuelllisten-Fingerprint (SHA-256): ${filter.digest.take(16)}…")}
        item{Button(onClick=update,enabled=!filter.updating){Text(if(filter.updating)"Wird geprüft …" else "Filter jetzt aktualisieren")}}
        if(settings.updateError.isNotEmpty())item{Text(settings.updateError,color=MaterialTheme.colorScheme.error)}
        item{Text("Automatische Filterupdates",fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0L,24L,48L,168L).forEach{h ->FilterChip(settings.updateHours==h,{interval(h)},label={Text(if(h==0L)"Aus" else "$h Stunden")})}}}
        item{Text("Darstellung",fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("system" to "System","light" to "Hell","dark" to "Dunkel").forEach{(value,title)->FilterChip(settings.theme==value,{theme(value)},label={Text(title)})}}}
        item{InfoCard("DNS-Resolver: Quad9 Secure","Erlaubte DNS-Anfragen gehen per HTTPS an dns.quad9.net/dns-query. Quad9 blockiert zusätzlich bekannte Bedrohungsdomains und kann DNSSEC-Fehler mit SERVFAIL beantworten. Keine TLS-Entschlüsselung, keine eigenen Server. Dies ersetzt weder Virenscanner noch umfassenden Phishing-Schutz. Der Resolver ist in dieser Version fest eingestellt.")}
        item{InfoCard("Dauerhafter Betrieb","Aktiviere zuerst den Schutz. Öffne danach Androids VPN-Einstellungen, wähle das Zahnrad bei AdShield und aktiviere „Durchgehend aktives VPN“. Lass „Verbindungen ohne VPN blockieren“ für die DNS-only-Architektur aus: Nicht-DNS-Verkehr soll direkt fließen. Auf Samsung bei Bedarf unter Akku → Grenzen der Hintergrundnutzung AdShield zu „Apps, die nie im Standby sind“ hinzufügen. Menünamen können abweichen.")}
        item{OutlinedButton(onClick=vpnSettings){Text("Android-VPN-Einstellungen öffnen")}}
        item{InfoCard("Private DNS und Browser","Android Private DNS, Chrome Secure DNS und eigene verschlüsselte Resolver anderer Apps können lokale DNS-Filter umgehen oder Konflikte auslösen. Prüfe diese Einstellungen manuell, falls keine Anfragen gezählt werden. Keine pauschale Abschaltung sicherer Verbindungen nötig. Im Maximalmodus werden mit aktivem Umgehungsschutz ausgewählte feste Resolver-Adressen erfasst. Unbekannte Ziele, eigene Resolver und feste Inhalts-IP-Adressen können den Schutz weiter umgehen. Es ist nur ein regulärer Android-VPN-Dienst gleichzeitig möglich.")}
        item{InfoCard("Datenschutz","Keine Konten, Werbung, Telemetrie oder Domainhistorie. Nur aggregierte Tageszähler werden lokal für bis zu 90 Tage gespeichert. Keine app-spezifischen DNS-Statistiken. Ausgehende Verbindungen: Quad9 für DNS und raw.githubusercontent.com für Filterupdates. Updates verraten dem Listenanbieter deine IP-Adresse. Einstellungen und Regeln bleiben auf dem Gerät; Android-Backups sind deaktiviert.")}
        item{LicenseCard()}
        item{InfoCard("AdShield 0.1.7 · Entwicklungsversion","Quelloffene native Android-App. Tests auf einem echten Galaxy S25 stehen noch aus. DNS-Filter können nicht alle Pop-ups und Weiterleitungen verhindern. Bei Problemen Schutz ausschalten und Android-VPN-Lockdown prüfen.")}
    }
}
@Composable private fun LicenseCard(){
    val context=androidx.compose.ui.platform.LocalContext.current
    var show by remember{mutableStateOf(false)}
    var text by remember{mutableStateOf("")}
    LaunchedEffect(show){if(show)text=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){runCatching{context.assets.open("LICENSES.txt").bufferedReader().use{it.readText()}}.getOrDefault("Lizenzhinweise konnten nicht geladen werden. Sie liegen auch im Quellprojekt.")}}
    OutlinedButton(onClick={show=true}){Text("Lizenzen und Filterquellen")}
    val licenseChunks=remember(text){text.chunked(2500)}
    if(show)AlertDialog(onDismissRequest={show=false},title={Text("Lizenzen")},text={LazyColumn(Modifier.heightIn(max=420.dp)){items(licenseChunks){chunk ->Text(chunk,style=MaterialTheme.typography.bodySmall)}}},confirmButton={TextButton(onClick={show=false}){Text("Schließen")}})
}
private fun percent(part:Long,total:Long)=if(total==0L)"0 %" else "%.1f %%".format(100.0*part/total)
private fun date(value:Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(value))
