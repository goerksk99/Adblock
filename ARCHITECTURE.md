# AdShield: Architekturentscheidung

Stand: 12.11.2026. Native Kotlin-App, Compose Material 3, MVVM (`ShieldModel`). Android 10/API 29 als Mindestversion; compile/target API 37 (Android 17). AGP 9.5.1, Gradle 9.6.0, integriertes Kotlin 2.2.10 und gleich versionierter Compose-Compiler. Das aktuelle offizielle SDK-Verzeichnis `repository2-3.xml` unterscheidet Plattformen 37.0/37.2; das ältere Verzeichnis reichte für diese Recherche nicht aus. Das Projekt verwendet API 37.0 ohne zusätzliche Minor-APIs. Build Tools 37.0.0. Alle direkten Versionen sind festgelegt; der Wrapper prüft die Distribution mit SHA-256.

## Datenfluss

Android-App → Betriebssystem-DNS → genau eine der virtuellen DNS-Adressen → TUN-Paketprüfung → UDP-Endpunkt oder begrenzter TCP-DNS-Endpunkt → Domainregeln → lokale NXDOMAIN-Antwort oder Quad9 DoH → DNS-Antwortprüfung/Cache → neues IP-Paket → Android-App.

`DnsVpnService` verwendet ausschließlich die Routen `10.111.0.2/32` und `fdad:51e1:d::2/128`, Adressen `10.111.0.1/32` und `fdad:51e1:d::1/128`, MTU 1500. **Keine Standardrouten** und kein vollständiger IP-Forwarder. Die tatsächlich installierten Routen wurden im API-29-Emulator mit `ConnectivityManager.getLinkProperties()` geprüft: die beiden DNS-Routen und zwei automatisch ergänzte /32-/128-Hostrouten für die eigenen Schnittstellenadressen, keine Standardroute. Auf dem Galaxy S25 muss dieselbe Prüfung zusätzlich erfolgen; ein Emulator ist kein Nachweis für jedes Gerät.

Normaler Nicht-DNS-Verkehr geht nicht durch unseren Paketcode. Fest eingebaute DNS-Server anderer Apps, DNS over TLS und eigene DoH-Verbindungen werden bewusst nicht umgeleitet. Die App schaltet Private DNS oder Browsereinstellungen nicht selbst um.

## DNS und TCP

`core/IpPacket.kt` prüft IPv4-Header, Längen, UDP/TCP-Prüfsummen, IPv6-Pseudoheader und begrenzte IPv6-Erweiterungsketten. `FragmentReassembler.kt` setzt IPv4-/IPv6-Fragmente vor der Transportprüfung zusammen: höchstens 64 Datengramme und insgesamt 1 MiB Fragmentdaten, fünf Sekunden Lebensdauer, höchstens 128 Fragmente pro Datengramm. Überlappungen verwerfen das ganze Datengramm. Auch Reihenfolgeänderungen und atomare IPv6-Fragmente sind getestet. EDNS/UDP-Antworten werden auf maximal 1232 Bytes beschränkt (ohne EDNS 512), größere Antworten erhalten TC für einen TCP-Neuversuch. Sehr große oder absichtlich überlappende Anfragen außerhalb dieser Grenzen werden verworfen; normale TCP-DNS-Anfragen bleiben davon unabhängig.

`core/TcpDnsServer.kt` ist ein eigener, auf DNS-Adressen begrenzter TCP-Endpunkt, kein universeller Netzwerkstack. Er verarbeitet Handshake, Sequenznummern einschließlich Überlauf, Längenrahmen über mehrere Segmente, mehrere Anfragen, Empfangsduplikate, ACKs, FIN/RST, höchstens 1200 Byte große Sendesegmente unter Beachtung des vom Peer angebotenen MSS (IPv4 ohne Angebot: 536 Byte), Fenstergrößen und begrenzte Wiederholungen. Pro Verbindung ist ein Segment ausstehend. Falsch geordnete Segmente werden mit dem erwarteten ACK beantwortet und vom Absender erneut gesendet. Maximal 128 Verbindungen, 30 Sekunden Inaktivität, begrenzte Ein-/Ausgabepuffer. Umfangreiche Tests unter realer Paketlast und bei seltenen TCP-Varianten bleiben erforderlich; diese Erstfassung ist kein auditiert bewährter TCP-Stack.

DNS-Nachrichten werden mit der gepflegten BSD-Komponente **dnsjava 3.7.1** gelesen/geschrieben statt einer eigenen Kompressionsparser-Implementierung. Wir verwenden deren Paketparser, nicht dessen eigenen Netzwerkresolver. Wire-IDs, Frage und Antwortflag werden abgeglichen. Lokale Sperren liefern NXDOMAIN mit SOA und 60 Sekunden negativer TTL, ohne gefälschtes DNSSEC-AD. Unterstützt werden alle regulären IN-Abfragetypen; AXFR/IXFR und Updates werden verweigert. A/AAAA sind explizit getestet. Fehler werden als FORMERR, REFUSED oder SERVFAIL beantwortet.

Der Cache enthält höchstens 512 vollständige Antworten, trennt alle Anfrageflags/EDNS-Optionen, altert TTLs mit monotoner Zeit und hält Einträge maximal fünf Minuten bzw. kürzer entsprechend der tatsächlichen TTL. Negative Antworten werden nur mit SOA-TTL gespeichert. Filterregeln werden vor jedem Cache-Zugriff ausgewertet. Nicht erfolgreiche Upstream-Antworten werden nicht gecacht.

## Ausgehende Verbindungen

* `https://dns.quad9.net/dns-query`: binäre DNS-Nachrichten per POST über OkHttp. Bootstrap-IP-Adressen aus Quad9s offizieller Dokumentation, SNI/Hostname und normale Zertifikatsprüfung bleiben erhalten. Jeder ausgehende Upstream-Socket wird nach Erzeugung seines nativen Deskriptors durch `bind()` und vor `connect()` mit `VpnService.protect()` geschützt; das Ergebnis wird geprüft. Ein nackter Android-`Socket()` kann noch keinen gültigen Deskriptor besitzen. Ab Version 0.1.1 prüft ein eigener Android-Test die tatsächliche native Schutzoperation sowie IPv4-/IPv6-Datenübertragung ohne Testresolver. Keine rekursive Betriebssystem-Auflösung des Resolvernamens. Zeitlimits und Antwortgröße sind begrenzt; keine unverschlüsselte Ausweichauflösung und kein eigener Server. Quad9 erhält die angefragten Domains und die öffentliche Quell-IP.
* `https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts`: HTTPS-Filterdownload, maximal 8 MiB, vollständige Format-/Mindestgrößenprüfung, Abgleich der von StevenBlack deklarierten eindeutigen Domainanzahl und Schutz vor ungewöhnlichem Größenrückgang. Keine Telemetrie. Der Listenanbieter erhält die Download-IP.

Keine Root-Zertifikate, kein TLS-MITM, kein Accessibility-Service, keine Root-Rechte. Androids System-Zertifikate sind die einzige App-Vertrauensquelle. DNSSEC-Validierung und zusätzliche Bedrohungssperren erfolgen bei Quad9; die App stellt hierfür keine eigene Garantie aus.

## Lebenszyklus und Ausfälle

Aktiv wird der Status nach erfolgreichem Upstream-Test, lokalem Filtertest und etablierter TUN-Schnittstelle. Zusätzlich wird vor Grün die tatsächlich vom Kernel gewählte Quelladresse für beide DNS-Routen über explizit ans VPN gebundene, ungesendete UDP-Verbindungen geprüft. Android kann LinkProperties bereits melden, bevor netd die Routen eingerichtet hat. Die Prüfung ist auf 15 Sekunden begrenzt und erzeugt keine DNS-Anfragen oder Statistikeinträge. Ein Ablauf wird als Startfehler behandelt und gibt den Tunnel frei. Der echte Paketweg wurde separat im API-29-Instrumentierungstest geprüft; andere Geräte bleiben zusätzliche Tests. Wiederholte Transportfehler schließen das VPN; es gibt keinen selbstgebauten endlosen Wiederanlauf. Netzereignisse leeren Verbindungen/Cache; neue Anfragen nutzen die aktuelle Verbindung. Android selbst kann Always-on erneut starten.

Always-on wird von Android verwaltet. Bei Always-on schaltet der App-Schalter in einen klar beschrifteten lokalen Filter-Pausenmodus; DNS geht weiterhin durch Quad9 und dessen Bedrohungsfilter. Sonst wird das VPN vollständig geschlossen. Wegen der spezifischen DNS-only-Routen ist Androids **„Verbindungen ohne VPN blockieren“ nicht unterstützt**; ein Start mit aktivem Lockdown wird abgelehnt. Die App kann diese Systemeinstellung nicht ändern. Sperrung durch Lockdown kann auch nach einem Fehler bestehen bleiben.

FGS-Typ `systemExempted` mit zugehöriger Berechtigung: Offizielle Dokumentation nennt konfigurierte VPN-Apps als berechtigt. Nur die fehlerhafte Alarmberechtigungs-Forderung des Lint-Detektors wird direkt am VPN-Service kommentiert unterdrückt; es wird keine unnötige Alarmberechtigung angefordert. Android 17s neue Audio-WIU-Regeln sind für den nicht audiobezogenen DNS-Dienst nicht relevant. Die neue LAN-Laufzeitberechtigung wird nicht angefordert: Die App entdeckt keine LAN-Geräte und verbindet sich nur zu öffentlichen Upstreams. Die offizielle Dokumentation nimmt Verkehr zum konfigurierten DNS-Server auf Port 53 von der LAN-Berechtigung aus. Diese Annahme muss auf Android 17 zusätzlich im Gerätetest bestätigt werden.

## Persistenz und Parallelität

DataStore: gewünschter Schutz, Domainregeln, App-Ausnahmen, Design, Updateintervall und letzte Updatefehlermeldung. Room: Tagesdatum und aggregierte Gesamt-/Erlaubt-/Blockiert-/Fehlerzähler, keine Domainhistorie. `StatsRecorder` begrenzt die Warteschlange auf 4096 Ereignisse und bündelt Schreibvorgänge mit 250 ms Verzögerung. INSERT OR IGNORE plus UPDATE in einer Transaktion funktioniert auch mit SQLite 3.22 auf Android 10; es wird kein dort inkompatibles SQL-UPSERT verwendet. Bei hartem Prozessabbruch können noch nicht geschriebene Ereignisse verloren gehen. Nach einem Datenbankfehler kennzeichnet die UI die Zähler als unvollständig. Jeder tatsächlich verarbeitete DNS-Rahmen zählt einmal, reine TCP-Wiederholungen nicht. Statistiken umfassen auch den Pausenbetrieb. Alte Tage werden beim Start und beim Tageswechsel im Dienst entfernt.

Filterupdates laufen etwa alle 24 Stunden mit WorkManager, Netzwerk- und Akkubedingungen, konfigurierbar auf 48/168 Stunden oder aus. Manuelle Updates verwenden dieselbe Repository-Instanz mit Mutex. `AtomicFile` übernimmt erst vollständig validierte Daten; ein Fehler erhält die letzte Arbeitsversion. Regeln werden als unveränderlicher Snapshot atomar ersetzt. Ausgehende Anfragen und TUN-Arbeit sind begrenzt; keine Busy-Loop und keine Domainlogs. TCP-Wiederholungstimer laufen nur, solange TCP-Verbindungen existieren; gewöhnlicher UDP-Betrieb erzeugt keine sekündliche Warteschleife. Android-Backups und Gerätetransfers sind deaktiviert.

## Offizielle Recherchequellen

Abgerufen am 12.11.2026:

* https://developer.android.com/develop/connectivity/vpn
* https://developer.android.com/develop/background-work/services/fgs/service-types
* https://developer.android.com/about/versions/17
* https://developer.android.com/about/versions/17/setup-sdk
* https://developer.android.com/about/versions/16/behavior-changes-16
* https://developer.android.com/about/versions/17/behavior-changes-17
* https://developer.android.com/training/package-visibility/declaring
* https://developer.android.com/about/versions/17/behavior-changes-all
* https://developer.android.com/build/releases/agp-9-5-0-release-notes
* https://dl.google.com/android/repository/repository2-3.xml
* https://docs.quad9.net/services/
* https://github.com/StevenBlack/hosts

Im Instrumentierungstest wird nur der Upstream über eine ausschließlich im Debug-Build ausgewertete In-Process-Testnaht ersetzt. Der echte Android-VPN-Dienst, TUN, Paketparser, Domainfilter, UDP/TCP und Datenbanken bleiben aktiv. Ohne ausdrücklich gestartete Instrumentierung ist die Testnaht null und die App verwendet Quad9. Es gibt keine fiktiven Werte oder Testschalter in der normalen Oberfläche. Testpaket-Beobachtung ist ebenfalls nur über diese Testnaht aktiviert und bleibt sonst vollständig aus. Die Tests binden eigene Client-Sockets ausdrücklich an das VPN-Netz, weil direkte Sockets der VPN-Eigentümer-App sonst die physische Verbindung wählen können.

Keine Behauptung getesteter Android-17- oder Galaxy-S25-Laufzeitkompatibilität ohne entsprechenden Testbericht.
