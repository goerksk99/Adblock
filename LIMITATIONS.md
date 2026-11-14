## DNS-Umgehungsschutz 0.1.3

Im voreingestellten Maximalmodus können die 20 ausgewählten Resolver-IPs nur noch DNS auf Port 53 über den lokalen Filter verwenden; andere Ports werden dort verworfen. Festes Browser-DoH, DoT oder Android Private DNS kann deshalb ausfallen. Der separate Schalter und der Standardmodus entfernen die zusätzlichen Routen. Hostnamen-Erlaubnisse öffnen keine Resolver-IP-Ports. Unbekannte Resolver und beliebige Inhalts-IP-Adressen bleiben unberührt. Keine allgemeine HTTPS-Entschlüsselung, kein Schutz gegen jede DNS-Umgehung.

## Maximalmodus 0.1.2

Der voreingestellte aggressive HaGeZi-Ultimate-Modus kann Webseiten und App-Funktionen beeinträchtigen. In Einstellungen lässt er sich ausschalten; Domain-Erlaubnisse haben Vorrang. Die Anzahl gelisteter Domains ist kein Prozentsatz blockierter Werbung. 43 bestandene Screenshot-Testfälle ersetzen keinen Live-Browsertest auf dem Nutzergerät. Die bestehenden DNS-, VPN- und Browser-Grenzen unten gelten weiterhin.

# Grenzen und offene Validierung

AdShield 0.1.7 ist eine **Entwicklungsversion**. Eine gebaute APK und Protokolltests ersetzen keine Prüfung des realen VPN-Paketwegs auf deinem Smartphone. Vor dauerhafter Nutzung bitte die Checkliste in TESTING.md durchgehen.

* DNS-Filter können bekannte Werbe-/Tracking-/Bedrohungsdomains sperren, aber keine beliebigen Seitenelemente entfernen oder jeden Pop-up-/Weiterleitungsmechanismus unterbinden. Seiten können bereits erlaubte Domains für störende Aktionen verwenden.
* Werbung und Inhalt unter derselben Domain können nicht zuverlässig getrennt werden. Keine Garantie für YouTube, Instagram oder TikTok. Das Sperren einer gemeinsamen Domain würde oft den Inhalt beschädigen.
* Private DNS, Browser-DoH, eigene Resolver und feste DNS-Adressen können den lokalen Filter umgehen oder Konflikte erzeugen. Notwendige manuelle Einstellungen stehen im App-Hilfetext. Es wird nichts heimlich umgestellt.
* **Android-VPN-Lockdown ausschalten.** Bei DNS-only werden normale IP-Verbindungen direkt geroutet; „Verbindungen ohne VPN blockieren“ kann diese sperren. Ein Start mit aktivem Lockdown wird abgelehnt. Bei späterer Aktivierung der Systemoption können Internet und App-Ausnahmen dennoch unterbrochen werden.
* Nur ein reguläres Android-VPN gleichzeitig. Andere VPN-Apps können die Freigabe ersetzen; AdShield kann sie nicht parallel nutzen.
* App-Liste: nur sichtbare startbare Apps, kein vollständiges Inventar. Auch nicht sichtbare Apps verwenden grundsätzlich den VPN-DNS, soweit Android sie einschließt. Es gibt keine erfundenen app-spezifischen DNS-Statistiken.
* Allowlist gilt global für die Domain und ihre Subdomains, mit Vorrang vor lokalen Sperren. Sie ist keine „ganze Website erlauben“-Funktion und umgeht Quad9s Bedrohungssperren nicht. Bereits vom Betriebssystem/Browser gecachte Antworten verschwinden durch eine Regeländerung nicht sofort.
* Quad9 ist fest gewählt; die Anzeige ist kein funktionsloser Resolver-Wahlschalter. Kein stiller Wechsel zu unverschlüsseltem DNS. Fallen alle Upstream-Verbindungen aus, wird das VPN nach wiederholten Fehlern getrennt. Android-Lockdown und Always-on können die Wiederherstellung beeinflussen.
* Fragment-Reassemblierung ist auf 64 Datengramme, insgesamt 1 MiB und fünf Sekunden begrenzt; überlappende, beschädigte oder übergroße Fragmente werden aus Sicherheitsgründen verworfen. ESP und unbekannte Transportprotokolle auf den beiden synthetischen DNS-Adressen werden nicht weitergeleitet. EDNS-Antwortgrenze 1232 Bytes reduziert ausgehende Fragmentierung. Das ist kein universeller IP-Stack.
* Eigener begrenzter TCP-DNS-Endpunkt: kein extern auditiertes Netzwerkprodukt. Seltene TCP-Optionen, sehr lange Transfers, Paketlast, alle Gerätevarianten und aggressives Pipelining benötigen weitere Tests. Idle-Verbindungen werden nach 30 Sekunden verworfen, maximal 128 gleichzeitig.
* Die Statistik wird kurz gebündelt geschrieben. Ein harter Prozessabbruch kann noch nicht gespeicherte Ereignisse verlieren; bereits gespeicherte Tageszähler bleiben erhalten. Bei einem Speicherfehler weist die Statistikseite auf unvollständige Zähler hin.
* Gesamtstatistik umfasst lokale Sperren, Fehler und erlaubte, eventuell vom Resolver mit NXDOMAIN beantwortete Abfragen. Andere DNS-Fehlercodes einschließlich SERVFAIL zählen als Fehler. „Erlaubt“ bedeutet vom lokalen Filter nicht gesperrt, nicht „Webseite funktioniert“. „Blockierte Anfragen“ sind keine Anzahl verhinderter Anzeigen.
* Hintergrundupdates können von Android, Akkuoptimierung und Netzwerkbedingungen verschoben werden. Samsung/One-UI-Verhalten, WLAN/Mobilfunk, Neustart, Banking und Videoplayer benötigen reale Gerätetests.
* Keine Garantie für Malware-/Phishing-Schutz. Keine eigene TLS-Prüfung des Browserverkehrs. DNS-Anfragen gehen an Quad9; Filterdownloads an GitHub. Beide Anbieter sehen die jeweilige Quell-IP.
* Debug-APK nur für private Entwicklung. Für längerfristige Nutzung eigener sicher verwahrter Release-Schlüssel erforderlich. Filterdaten enthalten unterschiedliche, teils nichtkommerzielle und Share-Alike-Lizenzen; nicht pauschal als MIT weiterveröffentlichen. Siehe Drittanbieterhinweise.

## Zusätzliche Grenzen 0.1.7

Alias-Schutz benötigt eine sichtbare DNS-Alias-Beziehung und eine passende aktive Sperrregel. Er umfasst CNAME, DNAME und HTTPS-/SVCB-Aliase, aber kein beliebiges Tracking hinter unveränderten Inhaltsdomains. Entfernte ServiceMode-Angebote sind keine vollständig gesperrte Originaldomain; deren legitimer A-/AAAA-Fallback bleibt erlaubt. Roh-IP-Verbindungen, unbekannte Resolver und Browser-Caches können weiterhin außerhalb dieser Prüfung liegen.

Warteschlangen-, Puffer- und Cache-Grenzen erhalten begrenzten Ressourcenverbrauch. Bei außergewöhnlicher Last können einzelne Anfragen SERVFAIL oder TCP-Reset erhalten und einen Neuversuch benötigen. Das ist keine unbegrenzte Lastzusage. Der DNS-Cache verwendet eine konservative Buchhaltung für Einträge, keine exakte Messung des gesamten Prozess-Heaps. 

Abgeschlossene Code- und Emulatorprüfungen belegen keine absolute Perfektion, keine vollständige Werbefreiheit und keine Bewertung von 10/10. Reale Akku-/Langzeitmessungen, Samsung-Hintergrundbetrieb und der tatsächliche Internetpfad auf dem Nutzertelefon bleiben zusätzliche Validierung.
