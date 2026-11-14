package de.adshield.core

/** Strong profile; user allow rules still take precedence. No content/TLS interception. */
object MaximalRules {
    const val URL = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/ultimate-onlydomains.txt"
    val supplemental: Set<String> = """
freshmarketer.com
claritybt.freshmarketer.com
fwtracks.freshmarketer.com
stats.wp.com
notify.bugsnag.com
sessions.bugsnag.com
api.bugsnag.com
app.bugsnag.com
events.reddit.com
events.redditmedia.com
ads-api.tiktok.com
analytics.tiktok.com
ads-sg.tiktok.com
analytics-sg.tiktok.com
business-api.tiktok.com
ads.tiktok.com
log.byteoversea.com
ads.yahoo.com
analytics.yahoo.com
geo.yahoo.com
udcm.yahoo.com
analytics.query.yahoo.com
partnerads.ysm.yahoo.com
log.fc.yahoo.com
gemini.yahoo.com
adtech.yahooinc.com
auction.unityads.unity3d.com
adserver.unityads.unity3d.com
extmaps-api.yandex.net
appmetrica.yandex.ru
adfstat.yandex.ru
metrika.yandex.ru
offerwall.yandex.net
adfox.yandex.ru
iot-eu-logser.realme.com
iot-logser.realme.com
iadsdk.apple.com
metrics.icloud.com
metrics.mzstatic.com
api-adservices.apple.com
api.ad.xiaomi.com
data.mistat.xiaomi.com
data.mistat.india.xiaomi.com
""".trimIndent().lineSequence().map(Domains::normalize).toSet()
    fun parse(text: String, minimum: Int = 50000): Set<String> {
        require(text.toByteArray(Charsets.UTF_8).size <= HostsParser.MAX_BYTES) { "Zusatzfilter zu groß" }
        require(text.startsWith("# Title: HaGeZi's Multi ULTIMATE") && text.contains("# Syntax: Domains")) { "Unbekanntes Zusatzfilterformat" }
        val expected = Regex("(?m)^# Number of entries: ([0-9]+)\\r?$").find(text)?.groupValues?.get(1)?.toIntOrNull()
        require(expected != null && expected in minimum..600000) { "Ungültige Anzahl im Zusatzfilter" }
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        val parsed = lines.map(Domains::normalize).toSet()
        require(lines.size == expected && parsed.size == expected) { "Zusatzfilter unvollständig; bisherige Listen bleiben aktiv" }
        return parsed
    }
    fun effective(base: Set<String>, maximum: Set<String>, enabled: Boolean, additional:Set<String> = emptySet()):Set<String> =
        if(enabled) HashSet<String>().apply{addAll(base);addAll(maximum);addAll(supplemental);addAll(additional)} else base
}
