# 0.1.6 – CNAME-Cloaking-Schutz

- CNAME-Ketten werden vom ursprünglichen Abfragenamen aus gegen aktive Sperrregeln geprüft, bis zu 16 Schritte.
- Zeigt eine Alias-Kette auf einen gesperrten Tracker, wird die ursprüngliche Anfrage lokal blockiert.
- Erlaubnisregeln behalten Vorrang; fremde Antwortnamen verursachen keine Sperre. Cache-Antworten werden bei Regeländerungen erneut geprüft. Pausierter Schutz bleibt durchlässig.
- 455.868 Listenregeln und die zusätzliche OISD-Abdeckung aus 0.1.5 bleiben erhalten.

# 0.1.5 – zusätzliche Domain-Abdeckung

- OISD Big zusätzlich im Maximalmodus: insgesamt 455.868 eindeutige Listenregeln; 121.559 OISD-Domains waren vorher auch durch keine Elternregel erfasst.
- Strenger Domain-Wildcard-Parser mit Format-/Anzahl-/Duplikat-/Größenprüfung; keine ignorierten Kontext- oder Regex-Ausnahmen.
- Gemeinsame atomare Updates aller drei Quellen; ungültige dritte Liste behält den bisherigen Stand. Bestehende Zwei-Listen-Caches werden beim Laden ergänzt.
- OISD-Quellheader und GPL-3.0-Lizenz enthalten. Standardmodus, Ausnahmen und übrige Schutzfunktionen unverändert.

# 0.1.4 – Stabilität und Diagnose

- In-App-Schutztest für lokale Sperre mit AdShield-Kennzeichnung und normale DNS-Auflösung; Hinweise bei striktem Android Private DNS.
- Nicht blockierende Statistik-Aggregation statt wartender DNS-Threads bei voller Ereigniswarteschlange; genaue Zähler bei angehaltener Datenbank getestet.
- Cache-Generation schützt vor erneutem Befüllen durch alte Antworten nach einem Netzwechsel.
- Verspätete Fehler alter Tunnels beeinflussen keine neu aufgebaute Verbindung.
- Gebündelte Netzwechsel-Prüfungen mit begrenzter Wiederholung; parallele Paketfehler beenden währenddessen nicht voreilig das VPN.
- App-Liste und Icons erst beim Öffnen des Apps-Tabs; verständliche Filter-Ladeanzeige.
- Domainregeln und DNS-Umgehungsschutz bleiben erhalten; Paketname und Signatur unverändert.

# 0.1.3 – bekannte DNS-Umgehungen einschränken

- Zusätzlicher voreingeschalteter, abschaltbarer Schutz im Maximalmodus: 20 exakte öffentliche Resolver-IP-Routen; direkte UDP/TCP-DNS-Anfragen gehen durch den lokalen Filter.
- Bekannte DoH-/DoT-Hostnamen blockiert. Andere Ports auf erfassten Resolver-IPs werden verworfen. Keine TLS-Entschlüsselung, keine Default-Route und keine geteilten CDN-Netze.
- AdShields eigene Quad9-Verbindung bleibt über geschützte Sockets unabhängig.
- Moduswechsel baut den aktiven Tunnel neu auf; Standardmodus und Always-on-Pause erfassen keine zusätzlichen Resolver-Adressen.
- 326.947 Listenregeln aus 0.1.2 bleiben erhalten. Unbekannte Resolver und Inhaltsdomain-Werbung bleiben Grenzen.

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
