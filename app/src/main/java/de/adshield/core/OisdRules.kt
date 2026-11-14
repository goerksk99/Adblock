package de.adshield.core

/** Domain-only wildcard format, no URL/regex rules or silently ignored exceptions. */
object OisdRules {
    const val URL="https://raw.githubusercontent.com/sjhgvr/oisd/main/domainswild_big.txt"
    fun parse(text:String,minimum:Int=50000):Set<String>{
        require(text.toByteArray(Charsets.UTF_8).size<=HostsParser.MAX_BYTES){"OISD-Datei zu groß"}
        return parse(text.reader(),minimum)
    }
    fun parse(reader:java.io.Reader,minimum:Int=50000,check:()->Unit={}):Set<String>{
        val result=HashSet<String>();var count=0;var expected:Int?=null;var title=false;var syntax=false
        FilterLines.each(reader,check){raw,_ ->
            val line=raw.trim()
            if(line=="# Title: oisd big")title=true
            if(line=="# Syntax: Domains (wildcards)")syntax=true
            if(line.startsWith("# Entries:"))expected=line.substringAfter(':').trim().toIntOrNull()
            if(line.isNotEmpty()&&!line.startsWith("#")){
                require(line.startsWith("*.")){"Ungültige OISD-Regel"}
                result.add(Domains.normalize(line.removePrefix("*.")));count++
            }
        }
        require(title && syntax && expected!=null && expected!! in minimum..600000 && count==expected && result.size==expected){"OISD unvollständig, doppelt oder ungültig"}
        return result
    }
}
