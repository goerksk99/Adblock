# 0.1.2 – stärkerer DNS-Schutz

- Maximalmodus voreingeschaltet: HaGeZi Ultimate plus gezielte Screenshot-Domains zusätzlich zu StevenBlack; 326.947 eindeutige Domainregeln.
- Schalter zum bisherigen Standardmodus; Erlaubnisregeln und App-Ausnahmen bleiben erhalten und haben Vorrang.
- Gemeinsame atomare Updates mit Quellen-, Größen-, Domain- und Anzahlprüfung. Bei Fehlern bleiben beide bisherigen Listen aktiv; ältere StevenBlack-Caches bleiben lesbar.
- Zusammengeführte Regeln werden einmal je Listenupdate berechnet; Einstellungen und Statistikänderungen erzeugen keine erneute große Mengenvereinigung.
- Neue Tests für alle gemeldeten Domains über A/AAAA/HTTPS und UDP/TCP, Ausnahmen, normale Inhalte und beschädigte Zusatzlisten.
- Paketname und Signatur bleiben unverändert. Datenbank unverändert, Update ohne Deinstallation.

# Änderungen

## 0.1.1

- Startfehler behoben: Den nativen TCP-Socket durch Bindung anlegen, bevor Android ihn vom VPN ausnimmt; erst danach verbinden.
- Alle SocketFactory-Überladungen binden und schützen in der richtigen Reihenfolge und schließen fehlgeschlagene Sockets.
- Fehlerursachen für den DNS-Start bleiben erhalten und werden verständlich und ohne Domainhistorie angezeigt.
- 58 Unit-Tests und 9 Android-Integrationstests bestanden, darunter echte native Socket-Protection- und IPv4-/IPv6-Übertragungstests.
- Dieselbe APK-Signatur und höherer Versionscode für ein Update ohne Datenverlust.

## 0.1.0

Erste native Entwicklungsfassung. Die damaligen VPN-Tests mit Testresolver deckten den jetzt korrigierten realen Socket-Aufbau zum Upstream nicht ab.
