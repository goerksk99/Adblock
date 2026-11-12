# Validierung der iOS-Entwicklungsfassung

Stand: 12. November 2026. Swift 6.0.3, Swift-Package-Sprachmodus 5, Linux x86_64.

**11 Tests bestanden, 0 Fehler.**

`swift test` prüft den plattformneutralen Kern: Normalisierung und Subdomain-Grenzen, Vorrang von Ausnahmen, Hosts-Aliase und Kommentare, mitgelieferte 78.215 Filterdomains/Lizenz, NXDOMAIN/SERVFAIL/TC, EDNS-Größen, DNS-ID/Question-Zuordnung, ungültige DNS-Nachrichten, IPv4-/IPv6-UDP-Prüfsummen, Verwerfen von Fragmenten, TCP-Handshake, geteilte DNS-Frames, MSS-Segmentierung, Replay-Unterdrückung, Wiederholungen, Ablauf und Verbindungslimit.

Die Swift-Dateien von App, gemeinsamem Speicher und Network Extension werden zusätzlich vom Swift-Frontend auf Syntax geprüft. Der Foundation-HTTP-Client wird auf Linux mit FoundationNetworking typgeprüft. Das ist ausdrücklich **keine** Typprüfung gegen das Apple-SDK und kein erfolgreicher iOS-App-Build.

Nicht verfügbar in dieser Linux-Umgebung: Xcode, iPhone-SDK, Apple-Signing-Team/Profile, verbundenes iPhone. Deshalb keine signierte IPA und keine Behauptung, der Tunnel sei bereits auf iOS gestartet. Das XcodeGen-Projekt ist für den nächsten Mac-Schritt vorbereitet.

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

Prüfprotokoll des Swift-Kerns liegt neben dieser Datei in `validation/core-tests.log`.
