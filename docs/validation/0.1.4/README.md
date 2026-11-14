# AdShield 0.1.4 – Stabilität, Startverhalten und Diagnose

## Geprüft

- 73 JVM-Tests bestanden, 0 Fehler/übersprungene Tests; Build und Android Lint erfolgreich.
- 10 Android-10-Instrumentierungstests im endgültigen Stand bestanden, Laufzeit 142,136 Sekunden.
- Statistik-Stresstest hält die Datenbank-Speicherung an, sammelt weitere 20.000 Ereignisse ohne auf SQLite zu warten und prüft nach Freigabe exakt 20.001 Gesamt-/20.000 Blockierungszähler. Gemessen werden Statistikbuchungen, kein Benchmark für 20.000 echte DNS-Netzwerkanfragen.
- Der in der Oberfläche verwendete ShieldModel-Schutztest besteht über den echten Android-TUN-Pfad mit einem kontrollierten Resolver. Lokale Sperre und normale DNS-Auflösung müssen gemeinsam funktionieren.
- Eine lokale Sperre benötigt zusätzlich die SOA-Kennzeichnung `blocked.invalid.`. Ein gewöhnliches NXDOMAIN vom Upstream kann deshalb keinen erfolgreichen lokalen Schutz vortäuschen. Ungültige IDs, fehlende Sperren und fehlgeschlagene Auflösung werden getrennt getestet.
- Ein nebenläufiger Cache-Test stellt sicher, dass eine nach Cache-Clear eintreffende alte Antwort den Cache nicht wieder füllt.
- Bisherige Tests für Domainregeln, direkte Resolver-IP-Anfragen, UDP/TCP, IPv4/IPv6, native Socket Protection, Statistik-Löschen, gespeicherte Einstellungen und atomare Filterupdates bestehen weiterhin.

## Änderungen

Statistiken werden pro Tag zusammengezählt und in kurzen Batches geschrieben. Es gibt keine wartenden DNS-Arbeitsthreads bei voller Ereigniswarteschlange mehr. Der Speicher ist auf acht Tagesgruppen begrenzt, Überläufe werden als unvollständige Statistik angezeigt; keine Domainhistorie.

Alte Tunnel-/Engine-Fehler werden vor und nach Lifecycle-Synchronisierung auf ihre Gültigkeit geprüft. Netzwechsel-Prüfungen werden kurz gebündelt; bei Transportfehlern gibt es einen begrenzten zweiten Versuch. Währenddessen entscheidet die Netzprüfung statt paralleler Fehlerpakete über das Beenden. Keine zusätzliche periodische Abfrage im Leerlauf.

App-Liste und Icons werden erst im Apps-Tab geladen. Beim Start erscheint eine Ladeanzeige für die Filter statt einer irreführenden Nullzahl. Unter Einstellungen kann der Nutzer den realen AdShield-DNS-Pfad prüfen; striktes Android Private DNS wird als möglicher Konflikt benannt. Ein Browser mit eigenem DNS ist dadurch nicht automatisch geprüft.

## Grenzen

Die Android-Netzwerktests verwenden einen kontrollierten Upstream und sind kein Live-Quad9-Test auf dem Galaxy S25. Akkuverbrauch, Mobilfunk-/WLAN-Wechsel unter realen Empfangsbedingungen, Always-on über Gerätestarts und lange Laufzeiten auf echten Geräten sind noch zu messen. Es gibt keine behauptete prozentuale Akkuersparnis und keine 10/10-Zertifizierung. Weiterhin private Entwicklungsfassung mit Debug-Signierung, keine App-Store-Freigabe.

Die 326.947 Listenregeln und der abschaltbare DNS-Umgehungsschutz bleiben unverändert. Inhaltsdomain-Werbung und unbekannte Resolver bleiben Grenzen. Paketname, Datenbankschema und Zertifikat unverändert: Update ohne Deinstallation.
