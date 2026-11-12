# AdShield – Downloads

Native Android-Entwicklungsfassung 0.1.1 für die private Nutzung.

- [APK herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-debug.apk)
- [Vollständiges Android-Studio-Projekt herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-source.zip)
- [SHA-256-Prüfsummen](downloads/SHA256SUMS)

58 Unit-Tests und 9 Android-10-Integrationstests bestanden. Die Android-VPN-Tests verwenden einen definierten Testresolver; Galaxy-S25-Alltagstests und echter Quad9-Betrieb auf dem Smartphone stehen noch aus. Vollständige Anleitung, Grenzen und Testergebnisse sind im Projektarchiv enthalten.

APK aufs Smartphone übertragen, öffnen und die Installation für die verwendete Datei-App gezielt erlauben. AdShield starten und Androids VPN-Freigabe bestätigen. „Verbindungen ohne VPN blockieren“ ausgeschaltet lassen.

DNS-Filter verhindern nicht jede Werbung oder Weiterleitung. Keine Garantie für YouTube-/Instagram-/TikTok-Werbeblockierung. Keine Telemetrie, kein Konto, kein eigener VPN-Server. Filterdaten und Bibliotheken behalten ihre jeweiligen Lizenzen; siehe Projektarchiv.

Version 0.1.1 korrigiert die Socket-Initialisierung vor Androids VPN-Ausnahme. Die Korrektur ist mit echten Android-Sockets über IPv4 und IPv6 geprüft. Das Signaturzertifikat bleibt gleich: APK als Update über die bestehende App installieren; eine Deinstallation ist nicht nötig.

## iOS-Entwicklungsfassung

[Swift-/XcodeGen-Projekt herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-iOS-source.zip) · [Anleitung](ios/README.md) · [Prüfsumme](downloads/AdShield-iOS-SHA256SUMS)

Systemweiter lokaler DNS-Filter mit SwiftUI, IPv4/IPv6, UDP/TCP, Quad9-DoH, Ausnahmen und Tagesstatistiken. 19 Swift-Tests jeweils in Debug und Release bestanden. **Noch keine installierbare IPA:** vollständiger Xcode-Build, Apple-Signierung und Tests auf einem echten iPhone stehen aus. Für den nächsten Schritt werden ein Mac mit Xcode und ein passendes Apple-Developer-Team benötigt. Es wurde nichts bei Apple veröffentlicht.

[Deine nächsten Schritte für iOS](ios/NEXT-STEPS.md) · [Automatischen Mac-Build ansehen](https://github.com/goerksk99/Adblock/actions/workflows/ios-check.yml). Der Workflow erzeugt ein unsigniertes Release-Archiv und prüft den Simulator-Start samt Screenshot; Apple-Signierung und iPhone-DNS-Test stehen weiterhin aus. Ein wartender CI-Lauf ist kein bestätigter iOS-Build.
