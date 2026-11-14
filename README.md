# AdShield – Downloads

Native Android-Entwicklungsfassung 0.1.4 für die private Nutzung.

- [APK herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-debug.apk)
- [Vollständiges Android-Studio-Projekt herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-source.zip)
- [SHA-256-Prüfsummen](downloads/SHA256SUMS)

73 Unit-Tests und 10 Android-10-Integrationstests bestanden. Die Android-VPN-Tests verwenden einen definierten Testresolver; Galaxy-S25-Alltagstests und echter Quad9-Betrieb auf dem Smartphone stehen noch aus. Vollständige Anleitung, Grenzen und Testergebnisse sind im Projektarchiv enthalten.

APK aufs Smartphone übertragen, öffnen und die Installation für die verwendete Datei-App gezielt erlauben. AdShield starten und Androids VPN-Freigabe bestätigen. „Verbindungen ohne VPN blockieren“ ausgeschaltet lassen.

DNS-Filter verhindern nicht jede Werbung oder Weiterleitung. Keine Garantie für YouTube-/Instagram-/TikTok-Werbeblockierung. Keine Telemetrie, kein Konto, kein eigener VPN-Server. Filterdaten und Bibliotheken behalten ihre jeweiligen Lizenzen; siehe Projektarchiv.

Version 0.1.1 korrigiert die Socket-Initialisierung vor Androids VPN-Ausnahme. Die Korrektur ist mit echten Android-Sockets über IPv4 und IPv6 geprüft. Das Signaturzertifikat bleibt gleich: APK als Update über die bestehende App installieren; eine Deinstallation ist nicht nötig.

## iOS-Entwicklungsfassung

[Swift-/XcodeGen-Projekt herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-iOS-source.zip) · [Anleitung](ios/README.md) · [Prüfsumme](downloads/AdShield-iOS-SHA256SUMS)

Systemweiter lokaler DNS-Filter mit SwiftUI, IPv4/IPv6, UDP/TCP, Quad9-DoH, Ausnahmen und Tagesstatistiken. 19 Swift-Tests jeweils in Debug und Release bestanden. **Noch keine installierbare IPA:** vollständiger Xcode-Build, Apple-Signierung und Tests auf einem echten iPhone stehen aus. Für den nächsten Schritt werden ein Mac mit Xcode und ein passendes Apple-Developer-Team benötigt. Es wurde nichts bei Apple veröffentlicht.

[Deine nächsten Schritte für iOS](ios/NEXT-STEPS.md) · [Automatischen Mac-Build ansehen](https://github.com/goerksk99/Adblock/actions/workflows/ios-check.yml). Der Workflow erzeugt ein unsigniertes Release-Archiv und prüft den Simulator-Start samt Screenshot; Apple-Signierung und iPhone-DNS-Test stehen weiterhin aus. Ein wartender CI-Lauf ist kein bestätigter iOS-Build.

## Android-Nachprüfung 14.11.2026

60 JVM-Tests, 9 Android-10-Gerätetests und Lint bestanden. 43 gemeldete Domains konkret untersucht: 27 von Standardregeln erfasst, 16 nicht. [Prüfbericht und Grenzen](docs/validation/2026-11-14/README.md). Das Android-Projektarchiv enthält die neuen Regressionstests; die APK bleibt unverändert Version 0.1.1.

## Android 0.1.2 – Maximaler Schutz

Voreingeschalteter Maximalmodus mit StevenBlack + HaGeZi Ultimate + gezielten Trackingregeln: **326.947 eindeutige Domainregeln**, zuvor 78.215. Alle 43 lesbaren Screenshot-Domains im lokalen DNS-Test blockiert; A/AAAA/HTTPS über UDP/TCP geprüft. 64 JVM-Tests und 9 Android-10-Tests bestanden, Build/Lint erfolgreich. Kein Live-Turtlecute-Test und keine Garantie vollständiger Werbefreiheit.

APK als Update installieren, nicht deinstallieren. Paketname und Signatur bleiben gleich, Daten und Ausnahmen bleiben erhalten. Maximaler Schutz kann App-Funktionen einschränken; bei Bedarf Domain erlauben oder unter Einstellungen auf Standardmodus zurückschalten. Beide Filterlisten werden atomar aktualisiert. [Nachweise und Grenzen](docs/validation/0.1.2/README.md). Die älteren Abschnitte oben beschreiben historische Prüfstände; aktuelle Downloads enthalten Version 0.1.2.

## Android 0.1.3 – zusätzliche DNS-Umgehungssperren

Aktuelle APK: **0.1.3**, als Update ohne Deinstallation. Maximalmodus ergänzt einen abschaltbaren Schutz für 20 exakte öffentliche Resolver-Adressen und bekannte verschlüsselte Resolver-Hostnamen. Direkte UDP-/TCP-DNS-Anfragen werden lokal gefiltert; andere Ports auf diesen Resolver-IP-Adressen werden verworfen. Kein TLS-Abfangen, keine Standardroute, keine vollständige Erfassung unbekannter Resolver. Festes Browser-DoH/Android Private DNS kann ausfallen; dann den separaten Schalter unter Einstellungen ausschalten.

67 JVM-Tests, 9 Android-10-Tests und ein gezielter zusätzlicher TUN-/Port-Test bestanden. Die 326.947 Listenregeln bleiben erhalten. [Aktuelle Nachweise](docs/validation/0.1.3/README.md). Für Inhalte innerhalb derselben Domain bleibt ein Browser-Inhaltsblocker sinnvoll. Alte Abschnitte oben dokumentieren frühere Versionen.

## Android 0.1.4 – Stabilität und Diagnose

Aktuelle APK: **0.1.4**, Update ohne Deinstallation mit identischem Zertifikat. Neuer Schutztest unter Einstellungen prüft den eigenen DNS-Pfad mit lokaler AdShield-Sperrkennzeichnung und normaler Auflösung. Statistik-Speicherung blockiert keine DNS-Arbeitsthreads mehr; Cache-Invalidierung und Fehlerisolation bei Tunnelwechseln verbessert. Netzwechsel-Prüfungen werden gebündelt und begrenzt wiederholt. App-Liste/Icons laden erst im Apps-Tab; verständliche Filter-Ladeanzeige.

73 JVM- und 10 Android-10-Tests bestanden, einschließlich angehaltener Datenbank mit 20.000 weiteren Statistikereignissen und In-App-Diagnose über realen TUN mit kontrolliertem Resolver. [Nachweise und Grenzen](docs/validation/0.1.4/README.md). Keine behauptete Akkuersparnis oder 10/10-Zertifizierung ohne echte Geräte-/Langzeittests. Listen und Grenzen der Blockierung bleiben unverändert.
