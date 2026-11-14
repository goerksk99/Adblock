# Teststrategie und Galaxy-S25-Prüfung

Stand: 12.11.2026. Resultate gelten nur für ausdrücklich genannte Läufe und Plattformen. Die abschließenden Messergebnisse stehen unten. Das Projekt ist eine Entwicklungsversion; keine Behauptung eines getesteten Galaxy S25.

## Automatisierte Prüfungen

```sh
python3 tools/cloud-gradle.py :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Alternativ außerhalb der Cloud: ./gradlew mit denselben Tasks.
```

Die JVM-Tests decken Domainnormalisierung/IDN, Regelvorrang, Labelgrenzen, die echte gebündelte Filterliste, beschädigte Listen, DNS A/AAAA, FORMERR/REFUSED/SERVFAIL, Zeitlimits, Antwortzuordnung, TTL/negative TTL/Cache, UDP-Truncation, TCP-Rahmen/Segmentierung/Wiederholungen/Fenster, IPv4/IPv6-Prüfsummen, Fragmentierung/Überlappungen/Timeouts und reproduzierbares Fuzzing ab.

Auf einem verbundenen kompatiblen Gerät:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w de.adshield.test/androidx.test.runner.AndroidJUnitRunner
```

Bei mehreren Geräten alle ADB-Befehle mit `-s SERIAL` ausführen. Alternativ `./gradlew :app:connectedDebugAndroidTest`. Den Runnerabschluss einschließlich Testanzahl prüfen; ein erfolgreicher ADB-Prozess allein beweist keinen Test-Erfolg.

Die Android-Tests prüfen echte Room-Persistenz, Tagesaggregation, gebündelte Statistikschreibvorgänge einschließlich Löschen, DataStore-Regeln/App-Ausnahmen, atomare Filterdateien einschließlich ungültiger/leerer/HTTP-Fehler-Updates und die vorinstallierte Liste. Die VPN-Prüfung erteilt nur auf dem Testgerät die Freigabe mit UIAutomations Shell-Rechten. Sie verwendet einen definierten lokalen Testresolver **statt Quad9**, verarbeitet aber echte Android-TUN-Pakete über IPv4/IPv6, UDP/TCP und den Produktionsfilter. Sie prüft die installierten Routen, NXDOMAIN, Allowlist und das Freigeben des VPN nach einem kontrollierten Upstream-Ausfall. Dies ersetzt keinen erfolgreichen Quad9-DoH-Test auf einem realen Android-Gerät.

Quellen für die Ergebnisse: JUnit-XML unter `app/build/test-results/testDebugUnitTest/`, Lint-Bericht unter `app/build/reports/`, kopierte Nachweise unter `docs/validation/`. APK-Signatur wird mit SDK `apksigner verify` geprüft. Ein read-only Quad9-Hosttest ist unter `tools/live-dns-smoke.py` reproduzierbar; er testet keine Android-Socket-Protection.

## Manuelle Checkliste auf dem Galaxy S25

Für jeden Punkt Datum, Android-/One-UI-Version, Ergebnis und auffälliges Verhalten notieren. Zuerst „Einstellungen → Telefoninfo → Softwareinformationen“ prüfen. WLAN und mobile Daten vor dem Test ohne AdShield ausprobieren. **Androids „Verbindungen ohne VPN blockieren“ ausgeschaltet lassen.**

- [ ] Debug-APK installieren, öffnen, Hell-/Dunkelmodus und alle fünf Bereiche prüfen. Keine echte Historie oder Phantasiewerte sollten erscheinen; nach realen DNS-Anfragen müssen Zähler zunehmen.
- [ ] Beim ersten Aktivieren Erklärung lesen, Android-VPN-Freigabe zunächst verweigern: kein aktiver Status. Danach erneut aktivieren und zustimmen. Die sichtbare Benachrichtigung prüfen; verweigerte Benachrichtigungsfreigabe darf nicht als VPN-Freigabe behandelt werden.
- [ ] Normale Webseiten, Anmeldung, Video-Streaming und die üblichen Apps ausprobieren. Auch Banking-Apps testen. Fehler mit und ohne AdShield vergleichen.
- [ ] Eine bekannte gelistete Domain, etwa `doubleclick.net`, abfragen/aufrufen. Sie soll lokal gesperrt sein. Eine normale Domain wie `example.com` soll DNS-seitig erlaubt bleiben. Browser-Fehler und HTTP-Status sind nicht dasselbe wie DNS-Ergebnisse.
- [ ] Eigene Domain sperren, dann als Ausnahme erlauben und danach entfernen. Subdomains und ähnlich geschriebene andere Domains vergleichen. Browser/Android können frühere Ergebnisse bis zum TTL-Ablauf cachen; gegebenenfalls warten oder den Browser neu starten. Eine Allowlist erlaubt nicht alle Drittanbieter einer ganzen Webseite.
- [ ] Eine sichtbare App ausschließen, den kurzen VPN-Neuaufbau abwarten und ihre Verbindung prüfen. Ausnahme wieder entfernen. Dabei keine app-spezifischen DNS-Zahlen erwarten.
- [ ] Filter manuell aktualisieren. Dann ohne Internet erneut aktualisieren: Fehler sichtbar, vorherige Anzahl/Datei bleiben erhalten. Nach Rückkehr des Internets nochmals aktualisieren. Hintergrundupdate nach dem gewählten Intervall prüfen; Android darf es bei Akku-/Netzbeschränkungen verschieben.
- [ ] Tages-, Sieben- und Dreißigtagesansicht prüfen. Oberfläche schließen und erneut öffnen: gespeicherte Tageswerte bleiben erhalten. Zum eindeutigen Löschtest vorher Schutz ausschalten, dann Statistiken löschen; sonst können sofort echte neue Anfragen hinzukommen.
- [ ] WLAN → Mobilfunk → WLAN wechseln. Normale Verbindungen und Status nach der DNS-Neuprüfung kontrollieren. Bei einem Fehler muss die App diesen anzeigen, statt fälschlich dauerhaft grün zu bleiben.
- [ ] Bildschirm mindestens 15 Minuten sperren, entsperren und erneut surfen. Später mehrere Stunden Hintergrundbetrieb und Akkuverbrauch beurteilen. Bei Samsung nötigenfalls die dokumentierten Standby-Ausnahmen bewusst selbst setzen.
- [ ] Always-on erst nach einem erfolgreichen Alltagstest einschalten. Smartphone neu starten und Wiederanlauf prüfen. In Always-on muss der App-Schalter den lokalen Filter klar als pausiert kennzeichnen; Quad9 bleibt aktiv. Zum vollständigen Trennen erst Always-on in Android ausschalten.
- [ ] VPN-Freigabe in Android widerrufen und gegebenenfalls ein anderes VPN wählen. AdShield darf danach keinen aktiven Schutz behaupten.
- [ ] Netz kontrolliert trennen, bis ein Fehler angezeigt wird; dann wieder verbinden. Internet muss bei ausgeschaltetem VPN-Lockdown normal wiederherstellbar sein. Kein unsichtbarer DNS-Blackhole-Zustand und kein endloser eigener Neustart-Loop.
- [ ] Android Private DNS und Browser Secure DNS prüfen. Wenn Anfragen die App umgehen, die Kompatibilitätshinweise beachten. Keine TLS-Zertifikate installieren und keine HTTPS-Prüfung ausschalten.
- [ ] Schutz ausschalten und prüfen, dass das VPN tatsächlich getrennt ist. Datenverkehr muss anschließend weiter funktionieren. Bei Always-on gilt der ausdrücklich bezeichnete Pausenmodus.

Optional mit USB-Debugging/Android Studio: `adb shell dumpsys connectivity` auf genau die beiden DNS-Routen und die eigenen Hostrouten prüfen, keine Standardrouten. Profiler für echten CPU-/RAM-Verbrauch und Energie verwenden. Die Cloud- und Emulatorwerte dürfen nicht als Galaxy-S25-Messungen übernommen werden.

## Bekannte Grenzen der Testumgebung

Kein echtes Galaxy S25 und kein Mobilfunkmodem standen zur Verfügung. Android 17 wurde gegen SDK API 37 kompiliert, nicht als Laufzeit getestet. Der Android-16/API-36-Emulator wurde tatsächlich versucht; ohne KVM musste nicht unterstützte Softwareemulation erzwungen werden. Das System startete Dienste neu und der Android-Paketinstaller scheiterte vor der App-Installation mit `NullPointerException` bei `StorageManager.getVolumes()`. Dort liefen keine App-Tests. Das ist ein Emulator-/Umgebungsfehler, kein bestandener oder fehlgeschlagener AdShield-Funktionstest.

Der Cloud-HTTPS-Verkehr verwendet die bereitgestellte Proxy-/CA-Konfiguration. Der positive Quad9-Hosttest belegt Erreichbarkeit aus der Cloud. Die App vertraut dagegen ausschließlich Androids System-CAs und enthält keine Cloud-CA oder TLS-Ausnahme; ihr echter Quad9-Betrieb muss auf dem Smartphone geprüft werden.

## Historischer Teststand 0.1.0

| Prüfung | Ergebnis |
| --- | --- |
| JVM-Unit-Tests | **53 bestanden**, 0 fehlgeschlagen, 0 übersprungen |
| Android 10/API 29, x86_64-AOSP-Emulator | **6 bestanden**, vollständiger abschließender Lauf in 74,878 Sekunden |
| Echter TUN-Paketweg | IPv4/IPv6, UDP/TCP, erlaubtes A/AAAA, lokales NXDOMAIN, Allowlist und kontrollierter Upstream-Ausfall geprüft |
| Tatsächliche Routen | Nur zwei DNS-Routen plus eigene Hostrouten; keine IPv4-/IPv6-Standardroute |
| Gradle Debug-Build und Lint | Erfolgreich, Lint: keine Befunde; eine eng dokumentierte FGS-Detektor-Ausnahme im Manifest |
| APK-Integrität | Debug-Signatur verifiziert; ZIP-Alignment einschließlich 16-KiB-Native-Library-Alignment bestanden |
| ARM64-native Bibliotheken | Beide enthaltenen Bibliotheken haben PT_LOAD-Alignment 16384 |
| Wiederverwendbares Cloud-Installationsskript | In der tatsächlichen Umgebung ausgeführt, erfolgreich; erneuter Build verwendete gültige vorhandene Ausgaben |
| Quad9 aus der Cloud | Reale HTTPS-DNS-Anfragen A und AAAA für example.com erfolgreich; etwa 452 bzw. 304 ms inklusive Prozess/TLS/Proxy |
| Oberfläche | Deutscher Hauptbildschirm im Emulator sichtbar geprüft; keine vollständige UI-/Barrierefreiheits-Automatisierung |
| Galaxy S25, WLAN/Mobilfunk, Samsung-Hintergrundbetrieb | **Nicht ausgeführt**, siehe manuelle Checkliste |
| Android 16/API 36 | Plattforminstaller scheiterte vor App-Installation, kein App-Test |
| Android 17/API 37 | Kompiliert, kein Laufzeittest |

Vorherige Pakettestläufe enthielten echte Timeouts: zuerst wählte der VPN-Eigentümer einen physischen Socket-Pfad; anschließend zeigte die Wiederholung, dass Android LinkProperties vor den fertigen Kernelrouten melden kann. Die Tests binden ihre Sockets an das VPN, und der Produktionsdienst prüft jetzt vor Grün die tatsächliche IPv4-/IPv6-Quelladresswahl. Der abschließende vollständige Lauf mit diesem Verhalten bestand. Ein früherer, sehr langsamer Instrumentierungslauf wurde abgebrochen und zählt nicht als Validierung. Fehler wurden nicht durch Abschalten von Paket-, TLS- oder Testprüfungen verborgen.

Host-Momentaufnahme des Filterbenchmarks (keine Galaxy-Messung, keine feste Leistungszusage):

```text
HOST_BENCHMARK domains=78215 parse_ms=362.636519 queries=100000 elapsed_ms=101.26175 average_us=1.0126175000000002
```

CPU und Akkubetrieb auf dem Galaxy wurden nicht gemessen. `emulator-ui-memory.txt` enthält nur eine Android-Emulator-Momentaufnahme beim Anzeigen der Debug-Oberfläche. Wegen Softwareemulation ist sie kein Smartphone-Leistungswert. Der Emulator zeigte auch einen System-UI-ANR; nach „Wait“ konnte die AdShield-Oberfläche angezeigt werden.

Ausgelieferte APK: `dist/AdShield-debug.apk`, 55,549,427 Bytes. SHA-256:

```text
837990e32ee7713b5abf25191f2b1b30967a13359cbef8a32615339ea2e7819b
```

Der echte Quad9-DoH-Betrieb einschließlich Socket Protection auf einem realen Android-Gerät, Always-on über Gerätestarts und die Alltagstauglichkeit bleiben ausdrücklich Teil der Telefonprüfung. Der Testresolver wird nicht als tatsächlicher Quad9-Verkehr ausgegeben.

## Update 0.1.1 – Startfehler beim DNS-Socket

Am 12.11.2026 wurde der gemeldete Startfehler untersucht. Die bisherige SocketFactory rief `protect()` auf einem noch nicht gebundenen Socket auf. Der echte Android-Test reproduziert hierfür `LEGACY_UNBOUND_SOCKET_PROTECTED=false bound=false`. Nach Bindung vor der Schutzoperation bestätigt derselbe Test `BOUND_SOCKET_PROTECTED_BEFORE_VPN=true`. Zusätzliche Tests übertragen tatsächlich Daten über geschützte IPv4- und IPv6-Loopback-Verbindungen. Weder Zertifikatsprüfung noch Socket Protection werden umgangen.

**Aktueller Stand: 58 Unit-Tests und 9 Android-10-Instrumentierungstests bestanden**, keine übersprungenen Unit-Tests. Der vollständige Android-Lauf dauerte 33,466 Sekunden. Build und Lint erfolgreich. APK-Signatur und 16-KiB-ZIP-Alignment geprüft; das Signaturzertifikat ist identisch mit 0.1.0, sodass ein Update ohne Deinstallation möglich ist. Die Nachweise stehen unter `docs/validation/0.1.1/`.

Transportfehler bleiben jetzt als Ursache erhalten. Die Oberfläche unterscheidet fehlgeschlagene Socket Protection, TLS-Probleme, Zeitüberschreitung, HTTP-Fehler und ungültige DNS-Antworten. Ausnahmen werden nicht mit möglichen Domainnamen im Klartext angezeigt oder protokolliert. Der reale Quad9-Verkehr auf dem Galaxy S25 ist weiterhin vom Nutzer zu prüfen; die bestätigte Socket-Korrektur ist kein vorgetäuschter Smartphone-Test.


## Android-Nachprüfung 14.11.2026

60 JVM-Tests und 9 Android-10-Instrumentierungstests bestanden; Lint erfolgreich. Konkrete Prüfung der 43 vollständig lesbaren Screenshot-Domains mit 129 Standardfilter- und 258 expliziten Sperrfällen. 27 Namen sind vom mitgelieferten Filter erfasst, 16 nicht. Details und Grenzen: `docs/validation/2026-11-14/README.md`. Keine neue APK und keine Änderung an Standardregeln.


## Aktuell: 0.1.2

64 JVM- und 9 Android-10-Tests bestanden; Lint und Build erfolgreich. 326.947 Regeln im Maximalmodus; 43/43 gemeldete Domains im lokalen DNS-Regressionstest blockiert. Vollständige Nachweise und Grenzen: `docs/validation/0.1.2/README.md`.


## Aktuell: 0.1.3

67 JVM-Tests und 9 Android-10-Tests bestanden. Direkte UDP-/TCP-DNS-Anfragen an festes Google DNS über IPv4 und IPv6 werden durch den lokalen Filter verarbeitet; Standardmodus entfernt die zusätzlichen Routen. Nachweise und Grenzen unter `docs/validation/0.1.3/README.md`.


## Aktuell: 0.1.4

73 JVM- und 10 Android-10-Tests bestanden. Zusätzlich DB-Stresstest, Schutztest über realen TUN, Cache-Invalidierung während laufender Anfrage und verbesserte Fehlerisolation. Nachweise und Grenzen: `docs/validation/0.1.4/README.md`. Keine Akku-/Langzeitbewertung auf dem Nutzergerät vorgetäuscht.


## Aktuell: 0.1.5

76 JVM- und 10 Android-10-Tests bestanden. 455.868 eindeutige Listenregeln, 121.559 erstmals abgedeckte OISD-Domains. Nachweise und Abgrenzung zur sichtbaren Werbequote: `docs/validation/0.1.5/README.md`.


## Aktuell: 0.1.6

82 JVM- und 10 Android-10-Tests bestanden. OISD ergänzt die Abdeckung; CNAME-Antworten werden jetzt gegen aktive Regeln geprüft. Listenregeln: 455.868. Vollständige Nachweise und Grenzen: `docs/validation/0.1.6/README.md`.
