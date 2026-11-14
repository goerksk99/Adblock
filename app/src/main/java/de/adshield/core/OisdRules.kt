package de.adshield.core

/** Domain-only wildcard format, no URL/regex rules or silently ignored exceptions. */
object OisdRules {
    const val URL="https://raw.githubusercontent.com/sjhgvr/oisd/main/domainswild_big.txt"
    fun parse(text:String,minimum:Int=50000):Set<String> {
        require(text.toByteArray(Charsets.UTF_8).size<=HostsParser.MAX_BYTES){"OISD-Datei zu groß"}
        require(Regex("(?m)^# Title: oisd big\\r?$").containsMatchIn(text) && text.contains("# Syntax: Domains (wildcards)")){"Unbekanntes OISD-Format"}
        val expected=Regex("(?m)^# Entries: ([0-9]+)\\r?$").find(text)?.groupValues?.get(1)?.toIntOrNull()
        require(expected!=null && expected in minimum..600000){"Ungültige OISD-Anzahl"}
        val result=HashSet<String>();var count=0
        text.lineSequence().map{it.trim()}.filter{it.isNotEmpty()&&!it.startsWith("#")}.forEach {
            require(it.startsWith("*.")){"Ungültige OISD-Regel"}
            result.add(Domains.normalize(it.removePrefix("*.")));count++
        }
        require(count==expected && result.size==expected){"OISD unvollständig oder doppelt; bisherige Listen bleiben aktiv"}
        return result
    }
}
