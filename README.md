# AdShield – Downloads

Native Android-Entwicklungsfassung 0.1.1 für die private Nutzung.

- [APK herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-debug.apk)
- [Vollständiges Android-Studio-Projekt herunterladen](https://github.com/goerksk99/Adblock/raw/refs/heads/main/downloads/AdShield-source.zip)
- [SHA-256-Prüfsummen](downloads/SHA256SUMS)

58 Unit-Tests und 9 Android-10-Integrationstests bestanden. Die Android-VPN-Tests verwenden einen definierten Testresolver; Galaxy-S25-Alltagstests und echter Quad9-Betrieb auf dem Smartphone stehen noch aus. Vollständige Anleitung, Grenzen und Testergebnisse sind im Projektarchiv enthalten.

APK aufs Smartphone übertragen, öffnen und die Installation für die verwendete Datei-App gezielt erlauben. AdShield starten und Androids VPN-Freigabe bestätigen. „Verbindungen ohne VPN blockieren“ ausgeschaltet lassen.

DNS-Filter verhindern nicht jede Werbung oder Weiterleitung. Keine Garantie für YouTube-/Instagram-/TikTok-Werbeblockierung. Keine Telemetrie, kein Konto, kein eigener VPN-Server. Filterdaten und Bibliotheken behalten ihre jeweiligen Lizenzen; siehe Projektarchiv.

Version 0.1.1 korrigiert die Socket-Initialisierung vor Androids VPN-Ausnahme. Die Korrektur ist mit echten Android-Sockets über IPv4 und IPv6 geprüft. Das Signaturzertifikat bleibt gleich: APK als Update über die bestehende App installieren; eine Deinstallation ist nicht nötig.
