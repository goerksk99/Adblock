package de.adshield.core

import org.xbill.DNS.*

/** Follow only the question's alias path, with explicit allow rules taking precedence. */
internal object AliasPolicy {
    fun apply(filter:FilterEngine,query:org.xbill.DNS.Record,response:Message,filtering:Boolean=true):Boolean {
        val enforce=filtering && !filter.allowed(query.name.toString())
        val answers=response.getSection(Section.ANSWER).filter{it.dClass==query.dClass}
        var current=query.name;val seen=HashSet<Name>()
        repeat(33){step ->
            require(seen.add(current)){"DNS-Alias-Schleife"}
            val cname=answers.filterIsInstance<CNAMERecord>().filter{it.name==current}.map{it.target}.distinct()
            require(cname.size<=1){"Widersprüchliche CNAME-Antwort"}
            val dnames=answers.filterIsInstance<DNAMERecord>().filter{current!=it.name && current.subdomain(it.name)}
            val closest=dnames.maxByOrNull{it.name.labels()}
            val dname=closest?.let { d ->
                require(dnames.filter{it.name==d.name}.map{it.target}.distinct().size==1){"Widersprüchliche DNAME-Antwort"}
                current.fromDNAME(d)
            }
            if(cname.isNotEmpty() && dname!=null)require(cname.single()==dname){"CNAME und DNAME widersprechen sich"}
            val service=if(query.type in setOf(Type.HTTPS,Type.SVCB))answers.filterIsInstance<SVCBBase>().filter{it.name==current && it.type==query.type} else emptyList()
            val svcAlias=service.filter{it.svcPriority==0}.map{it.targetName}.distinct()
            require(svcAlias.size<=1){"Widersprüchliche Service-Aliase"}
            if(cname.isEmpty() && dname==null && svcAlias.singleOrNull()==Name.root)return false
            val target=cname.singleOrNull()?:dname?:svcAlias.singleOrNull()
            if(target==null){
                // ServiceMode offers alternatives, not a rename of the original content origin.
                // Remove blocked bindings (including their hints), allowing ordinary A/AAAA fallback.
                val rejected=service.filter{enforce && it.svcPriority>0 && it.targetName!=Name.root && filter.blocked(it.targetName.toString())}
                if(rejected.isNotEmpty()){
                    rejected.forEach{response.removeRecord(it,Section.ANSWER)}
                    response.getSection(Section.ANSWER).filterIsInstance<RRSIGRecord>().filter{it.name==current && it.typeCovered==query.type}.forEach{response.removeRecord(it,Section.ANSWER)}
                    val names=rejected.map{it.targetName}.toSet()
                    response.getSection(Section.ADDITIONAL).filter{it.name in names && (it is ARecord || it is AAAARecord)}.forEach{response.removeRecord(it,Section.ADDITIONAL)}
                    response.header.unsetFlag(Flags.AD.toInt())
                }
                return false
            }
            require(step<32){"DNS-Alias-Kette zu lang"}
            if(enforce && filter.blocked(target.toString()))return true
            current=target
        }
        throw IllegalArgumentException("DNS-Alias-Kette zu lang")
    }
}
