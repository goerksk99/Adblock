# AdShield 0.1.7 – technische Qualitätsprüfung

Prüfstand: 15.11.2026 (Europe/Berlin). Android-Version 0.1.7, Versionscode 8. Diese Runde verbessert den bestehenden DNS-Filter; sie ist keine Zertifizierung vollständiger Werbefreiheit oder perfekter Qualität.

## Ergebnis und überprüfte Änderungen

| Bereich | Änderung und Nachweis |
| --- | --- |
| Alias-Schutz | CNAME, DNAME, HTTPS-/SVCB-AliasMode entlang der Frage, maximal 32 Schritte. Schleifen, widersprüchliche Ziele, exakter DNAME-Eigentümer, engste DNAME-Regel und unbeteiligte Namen getestet. |
| HTTPS-/SVCB-ServiceMode | Gesperrte Zielangebote einschließlich Adresshinweisen entfernt, zugehörige Signaturen verworfen und AD gelöscht. Legitime Alternativen, Root-Ziel und A-/AAAA-Fallback bleiben möglich. Cache-Ergebnisse reagieren auf neue Erlaubnisse. |
| DNS-Korrektheit | Ungültige Alias-Strukturen nicht gecacht. Upstream-DNS-Fehler getrennt von Transportausfällen. CD-Anfrageflag bei lokalen Fehlern erhalten. Bisherige TTL-, EDNS-, DNSSEC-Flag- und Antwortzuordnungsprüfungen bestehen. |
| Cache | Maximal 512 Einträge plus geschätztes 4-MiB-Budget für Rohantworten/Schlüssel/Overhead. Übergroße Einträge ausgeschlossen; Generationen verhindern alte Einträge nach Netzwechsel. |
| TCP | Produktive DNS-Auflösung außerhalb der Zustands-Sperre. Unabhängige Verbindungen, Antworten in anderer Fertigstellungsreihenfolge, Halb-Schließen, abgewiesene Arbeit und geschlossene Sitzungen getestet. Höchstens acht laufende Anfragen pro Verbindung. |
| Last | UDP: vier Arbeiter/64 Warteplätze; TCP-Eingang: einer/32; TCP-Auflösung: zwei/32. Maximal zwei Sekunden Wartealter. Überlast: lokales SERVFAIL oder TCP-Reset. Blockierte TCP-Auflösungen hindern UDP nicht. |
| Filter-Speicher | Zeilenweises Lesen und bytebegrenzte Streams, komprimierter atomarer Drei-Quellen-Snapshot, kein zusätzlich dauerhaft gehaltener Rohtext/HaGeZi-Satz. Alte JSON-Caches werden migriert, beschädigte Archive fallen sicher zurück. |
| Updates | HTTP-Abbruch, temporäre Dateien, unveränderte Quellhashes, ungültige Antworten, ungewöhnliche Verkleinerung und nicht beschreibbarer Zwischenspeicher geprüft. Alte Arbeitsversion bleibt bei Fehlern erhalten. Blockweise ZIP-Ausgabe vermeidet byteweise Dateischreibaufrufe. |
| Statistik | Ein Wecksignal pro ausstehendem Zählerbatch ersetzt Signale je Ereignis. Der Writer entnimmt Batches unter demselben kurzen Speicher-Lock; SQLite bleibt vollständig außerhalb der DNS-Arbeiter. Genaue Zähler und Löschen werden nativ geprüft. |
| Oberfläche | Doppelte manuelle Updatejobs unterbunden, verständliche Speicher-/Updatefehler, Lizenztexte abschnittsweise angezeigt, Quelllisten-Fingerprint klar beschriftet. |
| Installierbarkeit | Identischer Paketname und Signatur zum vorherigen APK, höherer Versionscode, APK-v2-Signatur, ZIP-/16-KiB-Alignment und ARM64-ELF-Alignment geprüft. |

## Automatisierte Ergebnisse

**108 JVM-Tests bestanden**, 0 Fehler, 0 übersprungen. Vollständiger Build von App und Test-APK erfolgreich. Android Lint: **keine Befunde**. JUnit-XML, Zusammenfassung, Buildausgabe und Lint-Ergebnis liegen in diesem Verzeichnis.

**Android 10/API 29: alle 15 Instrumentierungstests bestanden**, finaler Lauf in **206,886 Sekunden**. Der Runner schloss mit `OK (15 tests)` ab. Tests umfassen Room/DataStore, alte und neue Filter-Snapshots, abgebrochene/ungültige Updates, native Socket-Protection und echte Android-TUN-Pakete. DNS-Tests verwenden kontrollierte Resolverantworten, keine simulierten Zähler oder behaupteten Live-Browsertests.

**Android 16/API 36: alle 15 Instrumentierungstests bestanden**, finaler isolierter Lauf in **569,552 Sekunden**. Der Runner schloss mit `OK (15 tests)` ab. Nach regulärer Paketkompilierung startete die Instrumentierung ohne Startup-ANR; bestehende Fristen und Prüfungen blieben aktiv. Derselbe Produktionsstand wie im finalen Android-10-Lauf wurde installiert. Die Messzeiten sind wegen Softwareemulation keine Telefon-Leistungswerte.

Im Paketweg werden Aliasfälle über synthetische und feste Google-DNS-Ziele, jeweils IPv4/IPv6 sowie UDP/TCP geprüft. Zwei kontrolliert angehaltene TCP-Abfragen lassen UDP weiterarbeiten. Ein UDP-Burst bei angehaltenen Upstream-Arbeitern erhält lokales SERVFAIL, beendet den VPN-Dienst nicht und erholt sich nach Freigabe. In-App-Schutztest, Resolver-Port-Sperre, Erlaubnisregeln, Rückwechsel in den Standardmodus und kontrollierter Upstream-Ausfall sind weiterhin Teil des nativen Tests.

Die Android-10-Oberfläche wurde zusätzlich geöffnet: Einstellungen bis zur Versionsanzeige 0.1.7, Lizenzdialog mit abschnittsweisem Text und sichtbarer Schließen-Schaltfläche geprüft. Screenshot `license-dialog-android29.png`. Das ist ein begrenzter Oberflächentest, keine vollständige Barrierefreiheitsprüfung.

## Gefundene Fehler und abgebrochene Versuche

Ein früherer Android-10-Lauf dieser Runde wurde nach einem beobachteten langen Filter-Speichervorgang zur Untersuchung beendet. Der Thread-Dump zeigte `FilterOutputStream.write`: dessen Standardimplementierung schrieb komprimierte Blöcke byteweise. Die Ausgabe delegiert nun ganze Blöcke an die AtomicFile-Datei. Der oben genannte finale Lauf prüft die korrigierte Fassung vollständig; der abgebrochene Lauf zählt nicht als bestanden.

Ein erster vollständig abgeschlossener Android-16-Lauf bestand 14 von 15 Tests. Der Produzent des Statistik-Stresstests überschritt die bestehende Zehn-Sekunden-Grenze bei 20.000 Ereignissen. Jeder Aufruf sendete zuvor ein eigenes conflated Wecksignal. Die Produktionslogik sendet nun nur beim Übergang von einem leeren zu einem ausstehenden Batch. Writer-Entnahme und neue Ereignisse verwenden denselben Speicher-Lock; SQL bleibt davon unabhängig. Der Test und sein Zeitlimit wurden nicht abgeschwächt. Die endgültigen Läufe verwenden diese Optimierung. Auf Android 10 benötigte der Zählerproduzent 210 ms, auf Android 16 1.695 ms; jeweils bei gesperrtem Datenbank-Writer und anschließend exakt 20.001 gespeicherten Ereignissen. Das sind Emulatorwerte, keine Smartphone-Leistungszusage.

Der frische Android-16/API-36-Emulator konnte diesmal vollständig starten und beide APKs installieren. Erste Instrumentierungsversuche brachen vor dem ersten Test durch Start-ANRs im Android-Dex-Lader ab. Das System zeigte auch Watchdog-/Telefon-/Bluetooth-Ausfälle. Softwareemulation ohne KVM ist hier keine belastbare Grundlage für Leistungswerte. Auch beim Öffnen der Android-10-Oberfläche erschien ein System-UI-ANR; nach „Wait“ wurde AdShield angezeigt. Das war ein System-UI-Dialog, kein als erfolgreicher UI-Test ausgegebener App-Start. Nach regulärer Paketkompilierung wurde ein isolierter Lauf vollständig ausgeführt; nach der Statistikoptimierung bestand die endgültige Wiederholung alle 15 Tests. Die ursprünglichen ANR- und Fehlversuchsnachweise bleiben erhalten.

## Grenzen und nächste reale Validierung

Die Listen bleiben unverändert: **455.868 eindeutige Regeln** aus StevenBlack, HaGeZi Ultimate, OISD Big und gezielten Ergänzungen. Mehr Verarbeitungsverfahren erhöhen die Erfassung möglicher Tracker-Aliase, liefern aber keinen gemessenen Prozentsatz entfernter sichtbarer Anzeigen. Dienst-/Fehlerbericht-Domains sind nicht sämtlich Werbung.

Keine Live-Behauptung für adblock.turtlecute.org: Die Cloud-Netzrichtlinie verhindert den Zugriff auf diese Website. Eigene Domain-/Paketregressionen ersetzen keinen solchen Browsertest. Kein echter Galaxy-S25-, Mobilfunk-, Akku-, Samsung-Hintergrund-, Always-on-Neustart- oder Langzeitversuch. Android 17/API 37 kompiliert, nicht als Laufzeit getestet. Keine neue iOS-Validierung durch diese Android-Runde.

DNS kann Werbung aus derselben Inhaltsdomain, unbekannte Resolver oder bereits im Browser gespeicherte Verbindungen nicht vollständig kontrollieren. Ein Browser-Inhaltsblocker kann zusätzlich Seitenelemente filtern. Maximalmodus kann Funktionen beeinträchtigen; erlaubte Domains, App-Ausnahmen und Standardmodus bleiben verfügbar. Keine TLS-Entschlüsselung, keine Default-Route, keine Domainhistorie und keine zusätzlichen App-Berechtigungen.

Es gibt keinen seriösen Nachweis, dass an einer Software nie mehr etwas verbessert werden kann. Die hier ausgeführten Verbesserungen und Tests sind konkret dokumentiert; nicht ausgeführte Gerätetests werden nicht als erledigt ausgegeben.
