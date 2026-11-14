package de.adshield.core

import java.net.IDN
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

object Domains {
    fun normalize(input: String): String {
        val text = input.trim().removeSuffix(".")
        require(text.isNotEmpty() && text.none { it.isWhitespace() || it in "/:@*" }) { "Bitte nur einen Domainnamen eingeben, ohne URL oder Platzhalter." }
        // Hosts lists and DNS wire names are overwhelmingly ASCII. Avoid ICU/IDN work
        // on that path; apply the same STD3 label rules directly. Unicode input still
        // goes through the platform's IDN/Punycode implementation.
        val ascii = (if (text.all { it.code < 128 }) text else IDN.toASCII(text, IDN.USE_STD3_ASCII_RULES)).lowercase(Locale.ROOT)
        require(ascii.length <= 253 && ascii.contains('.')) { "Bitte einen vollständigen Domainnamen eingeben." }
        var start = 0
        for (index in 0..ascii.length) {
            if (index == ascii.length || ascii[index] == '.') {
                require(index - start in 1..63 && ascii[start] != '-' && ascii[index - 1] != '-') { "Ungültiger Domainname." }
                start = index + 1
            } else {
                require(ascii[index] in 'a'..'z' || ascii[index] in '0'..'9' || ascii[index] == '-') { "Ungültiger Domainname." }
            }
        }
        require(!ascii.all { it in '0'..'9' || it == '.' }) { "IP-Adressen sind keine Domainregeln." }
        return ascii
    }
    fun matches(domain: String, rules: Set<String>): Boolean {
        var candidate = domain
        while (true) {
            if (candidate in rules) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}

data class RuleSet(val hosts: Set<String> = emptySet(), val allow: Set<String> = emptySet(), val deny: Set<String> = emptySet(), val resolverGuard: Boolean = false) {
    fun blocked(domain: String): Boolean {
        val name = runCatching { Domains.normalize(domain) }.getOrNull() ?: return false
        return !Domains.matches(name, allow) && (Domains.matches(name, deny) || Domains.matches(name, hosts) || (resolverGuard && Domains.matches(name, DnsBypassPolicy.names)))
    }
}
class FilterEngine {
    private val state = AtomicReference(RuleSet())
    fun replace(rules: RuleSet) { state.set(rules) }
    fun blocked(domain: String): Boolean = state.get().blocked(domain)
    fun allowed(domain:String):Boolean { val name=runCatching{Domains.normalize(domain)}.getOrNull()?:return false;return Domains.matches(name,state.get().allow) }
}

object HostsParser {
    const val MAX_BYTES = 8 * 1024 * 1024
    private val whitespace = Regex("\\s+")
    fun parse(text: String, minimum: Int = 1000): Set<String> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Filterdatei ist zu groß." }
        require(text.startsWith("#") && text.contains("StevenBlack/hosts")) { "Unbekanntes Filterformat." }
        val domains = HashSet<String>()
        var malformed = 0
        text.lineSequence().forEach { line ->
            val fields = line.substringBefore('#').trim().split(whitespace)
            if (fields.firstOrNull() == "0.0.0.0" || fields.firstOrNull() == "127.0.0.1") {
                fields.drop(1).forEach { field ->
                    val domain = runCatching { Domains.normalize(field) }.getOrNull()
                    if (domain != null) domains.add(domain) else if (field !in setOf("localhost", "local", "0.0.0.0", "localhost.localdomain")) malformed++
                }
            }
        }
        val declared = Regex("Number of unique domains: ([0-9,]+)").find(text)?.groupValues?.get(1)
        if (declared != null) {
            val expected = declared.replace(",", "").toIntOrNull()
            require(expected != null && expected == domains.size) { "Filterdownload ist unvollständig; bisherige Liste bleibt erhalten." }
        }
        require(domains.size >= minimum && malformed <= 100) { "Filter unvollständig oder beschädigt; bisherige Liste bleibt erhalten." }
        return domains
    }
}
