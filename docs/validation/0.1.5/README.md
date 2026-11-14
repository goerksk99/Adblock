# AdShield 0.1.5 – zusätzliche Abdeckung durch OISD Big

76 JVM-Tests und 10 Android-10-Instrumentierungstests bestanden, Build und Lint erfolgreich. Android-Laufzeit 205,845 Sekunden. Gleicher Paketname und gleiche Signatur, versionCode 6: Installation als Update ohne Deinstallation.

## Messung

- OISD Big: 228.023 gültige Domain-Wildcardregeln. Herkunft: https://raw.githubusercontent.com/sjhgvr/oisd/main/domainswild_big.txt ; GPL-3.0, Header unverändert beigefügt.
- Vorheriger Maximalmodus: 326.947 eindeutige Listenregeln. Neuer Maximalmodus: 455.868.
- 121.559 OISD-Domains waren zuvor auch durch keine übergeordnete Regel abgedeckt. Rohzahlen der Mengenvereinigung und tatsächliche zusätzliche Abdeckung sind getrennt ermittelt.
- 100 neu erfasste Domains über A/AAAA/HTTPS und UDP/TCP geprüft: 600 Fälle, alle lokal blockiert, kein Test-Upstream-Aufruf erlaubt.
- Weitere 1.000 neue Namen mit Subdomains/Label-Grenzen geprüft; Erlaubnisvorrang und normale Inhaltsdomains bleiben erhalten.
- Beschädigte, unvollständige, doppelte und nicht unterstützte Regeln werden zurückgewiesen.
- Echte Android-Tests bestätigen Laden, gemeinsam gespeicherten Snapshot, Erhalt alter Listen bei fehlerhaften OISD-Antworten sowie Statistik-Stresstest, In-App-Diagnose und TUN-Paketpfad. Die größere Kombination hat den vollständigen Testlauf ohne App-Prozessabsturz bestanden; dies ist keine Messung des RAM- oder Akkuverbrauchs auf dem Nutzertelefon.

## Grenzen

Zusätzliche Domains umfassen Werbung, Tracking und schädliche Dienste. Die Zahlen sind kein Prozentsatz entfernter sichtbarer Werbung. Kein Live-Turtlecute-Test, keine vollständige Erfassung eigener Resolver und keine Garantie gegen Inhaltsdomain-Werbung. Stärkeres DNS-Blocking kann App-Funktionen beeinträchtigen; Erlaubnisse, App-Ausnahmen und Standardmodus bleiben verfügbar. Native Netzwerktests verwenden einen kontrollierten Resolver, nicht Quad9 auf dem Nutzertelefon. Allgemeine technische Grenzen aus 0.1.4 bleiben gültig.
