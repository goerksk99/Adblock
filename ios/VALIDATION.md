# Validierung der iOS-Entwicklungsfassung

Stand: 12. November 2026. Swift 6.0.3, Swift-Package-Sprachmodus 5, Linux x86_64.

**19 Tests in Debug und 19 in Release bestanden, jeweils 0 Fehler.**

`swift test` prüft den plattformneutralen Kern: Normalisierung und Subdomain-Grenzen, Vorrang von Ausnahmen, Hosts-Aliase und Kommentare, mitgelieferte 78.215 Filterdomains/Lizenz, NXDOMAIN/SERVFAIL/TC, EDNS-Größen, DNS-ID/Question-Zuordnung, ungültige DNS-Nachrichten, IPv4-/IPv6-UDP-Prüfsummen, Verwerfen von Fragmenten, TCP-Handshake, geteilte DNS-Frames, MSS-Segmentierung, Replay-Unterdrückung, Wiederholungen, Ablauf und Verbindungslimit.

Zusätzlich geprüft: echte URLSession-Delegate-Verarbeitung über einen lokalen URLProtocol-Testtransport (POST/HTTPS-Endpunkt, gültige Binärantwort, HTTP-503, falscher MIME-Typ, zu kurze und zu große Antworten), strenge Hosts-Validierung einschließlich deklarierter Anzahl und Root-Domain-Abfragen. Kein öffentlicher Quad9- oder iPhone-Test wird dadurch vorgetäuscht.

Die Swift-Dateien von App, gemeinsamem Speicher und Network Extension werden zusätzlich vom Swift-Frontend auf Syntax geprüft. Das Networking-Modul wird mit FoundationNetworking als Teil des Swift-Pakets kompiliert und getestet. Das ist ausdrücklich **keine** Typprüfung gegen das Apple-SDK und kein erfolgreicher iOS-App-Build.

Nicht verfügbar in dieser Linux-Umgebung: Xcode, iPhone-SDK, Apple-Signing-Team/Profile, verbundenes iPhone. Deshalb keine signierte IPA und keine Behauptung, der Tunnel sei bereits auf iOS gestartet. Das XcodeGen-Projekt ist vorbereitet; ein GitHub-Actions-Workflow für macOS wurde eingerichtet und ausgelöst. Der zuletzt beobachtete Lauf wartete noch auf einen Runner. Vor einem grünen Abschluss gilt der Apple-SDK-Build weiterhin als unbestätigt.

Vor einer installierbaren Freigabe auf einem echten iPhone prüfen:

1. `scripts/build-macos.sh` erfolgreich ausführen; Warnungen/SDK-Typfehler beheben, dann beide Targets signieren.
2. App-Group-Dateien und Filterliste erreichbar; fehlende Profile/Entitlements dürfen keine falsche Aktivmeldung erzeugen.
3. VPN-Freigabe, wiederholtes Starten/Stoppen, App-Wechsel, Sperrbildschirm, Neustart und Deinstallation der VPN-Konfiguration.
4. WLAN, Mobilfunk und IPv6-Netz; Wechsel dazwischen; fehlendes Internet; unerreichbares Quad9; Zertifikatsfehler ohne TLS-Umgehung.
5. Echte UDP- und TCP-DNS-Abfragen einschließlich Antworten über 512/1232 Bytes, segmentierten Frames, Paketverlust und MSS.
6. Bekannte Werbedomain blockiert, normale Domain erreichbar, Erlaubnisregel priorisiert, manuelle Sperre wirksam; Änderungen während aktiven Tunnels.
7. Filterupdate atomar, ungültige/zu große/inkonsistente Downloads verwerfen, alte Liste weiter verwendbar.
8. Tageswechsel und Zähler; kein Domain-Verlauf in App-Group-Dateien; Extension-Speicherverbrauch unter realer Last.
9. Anderes VPN und Apps mit eigenem DNS als erkennbare Produktgrenzen prüfen.

Prüfprotokolle liegen neben dieser Datei in `validation/core-tests.log` und `validation/release-tests.log`. Die Shell-Skripte wurden syntaktisch geprüft; der Signierungshelfer wurde mit gültigen und ungültigen Eingaben isoliert geprüft. Projektpfade, Entitlement-/Info-Daten und xcconfig-Pfade wurden strukturell geprüft.
