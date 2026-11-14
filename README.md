# AdShield – Downloads

Private native Android-Entwicklungsfassung **0.1.7** (Versionscode 8).

- [Android-APK herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-debug.apk)
- [Android-Studio-Projekt herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-source.zip)
- [SHA-256-Prüfsummen](downloads/SHA256SUMS)
- [Aktueller Testbericht](docs/validation/0.1.7/README.md)

**Als Update über die bestehende App installieren; nicht deinstallieren.** Paketname und Signatur bleiben gleich, Regeln und Statistiken bleiben erhalten. Danach AdShield öffnen und den Schutz aktivieren. Androids „Verbindungen ohne VPN blockieren“ ausgeschaltet lassen.

## Android 0.1.7

455.868 eindeutige Listenregeln aus StevenBlack, HaGeZi Ultimate, OISD Big und gezielten Ergänzungen. Zusätzlich CNAME-/DNAME- und HTTPS-/SVCB-Aliasprüfung. ServiceMode entfernt nur gesperrte Zielangebote, legitime Alternativen bleiben möglich. Erlaubte Domains und App-Ausnahmen behalten Vorrang.

Statistik-Wecksignale sind auf eines je wartendem Zählerbatch reduziert. Diese Qualitätsrunde begrenzt Cache und Arbeitswarteschlangen, trennt TCP-Auflösung vom Verbindungs-Lock und beantwortet Überlast lokal. Filter werden zeilenweise gelesen und atomar komprimiert gespeichert; alte Caches werden migriert, unveränderte Quellen nicht erneut verarbeitet und abgebrochene Updates behalten die letzte Arbeitsversion. Große Lizenztexte werden abschnittsweise angezeigt.

**108 JVM-Tests und jeweils alle 15 Integrationstests auf Android 10 und Android 16 bestanden.** Build, Lint, APK-Signatur und 16-KiB-Ausrichtung geprüft. Native Tests verwenden kontrollierte Resolverantworten und echte TUN-Pakete über IPv4/IPv6 sowie UDP/TCP. Vollständige Plattformresultate und Einschränkungen im Testbericht.

Kein Nachweis perfekter Werbefreiheit: Werbung aus Inhaltsdomains, unbekannte Resolver und Browser-Caches bleiben Grenzen. Kein Live-Turtlecute-, Galaxy-S25-Akku-, Mobilfunk- oder Langzeittest. Für Seitenelemente kann ein Browser-Inhaltsblocker ergänzen. Maximalmodus kann App-Funktionen beeinträchtigen; Erlaubnisse und Standardmodus bleiben verfügbar.

Keine Konten, Telemetrie, Domainhistorie, TLS-Entschlüsselung oder eigenen VPN-Server. Filterdaten und Bibliotheken behalten ihre jeweiligen Lizenzen. Vollständige Android-Anleitung im Projektarchiv; weitere Einzelheiten unter [Architektur](ARCHITECTURE.md), [Tests](TESTING.md) und [Grenzen](LIMITATIONS.md).

## iOS-Entwicklungsfassung

[Swift-/XcodeGen-Projekt herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-iOS-source.zip) · [Anleitung](ios/README.md) · [Prüfsumme](downloads/AdShield-iOS-SHA256SUMS)

Systemweiter lokaler DNS-Filter mit SwiftUI, IPv4/IPv6, UDP/TCP, Quad9-DoH, Ausnahmen und Tagesstatistiken. 19 Swift-Tests jeweils in Debug und Release bestanden. **Noch keine installierbare IPA:** vollständiger Xcode-Build, Apple-Signierung und Tests auf einem echten iPhone stehen aus. Für den nächsten Schritt werden ein Mac mit Xcode und ein passendes Apple-Developer-Team benötigt. Es wurde nichts bei Apple veröffentlicht.

[Deine nächsten Schritte für iOS](ios/NEXT-STEPS.md) · [Automatischen Mac-Build ansehen](https://github.com/goerksk99/Adblock/actions/workflows/ios-check.yml). Der Workflow erzeugt ein unsigniertes Release-Archiv und prüft den Simulator-Start samt Screenshot; Apple-Signierung und iPhone-DNS-Test stehen weiterhin aus. Ein wartender CI-Lauf ist kein bestätigter iOS-Build.

## Frühere Android-Prüfstände

[0.1.6](docs/validation/0.1.6/README.md) · [0.1.5](docs/validation/0.1.5/README.md) · [0.1.4](docs/validation/0.1.4/README.md) · [0.1.3](docs/validation/0.1.3/README.md) · [0.1.2](docs/validation/0.1.2/README.md)
