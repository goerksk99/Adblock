# AdShield 0.1.6 – zusätzliche Abdeckung und CNAME-Cloaking-Schutz

OISD Big aus 0.1.5 bleibt zusätzlich aktiv: 455.868 eindeutige Listenregeln; 121.559 OISD-Domains waren vorher durch keine Listen-/Elternregel erfasst. Dies umfasst auch Tracking- und schädliche Domains, nicht nur Werbung. Die Eintragszahl ist keine Prozentquote für entfernte sichtbare Werbung.

Neu ist die Prüfung von CNAME-Alias-Ketten einer validierten DNS-Antwort. Nur die vom ursprünglichen Abfragenamen ausgehende Kette wird verfolgt, maximal 16 Schritte. Ein gesperrtes Ziel führt zu lokalem NXDOMAIN mit AdShield-Kennzeichnung. Erlaubte Originalnamen und erlaubte Zielnamen behalten Vorrang. Fremde Antwortnamen werden ignoriert. Auch Cache-Antworten werden nach Regeländerungen erneut geprüft. Filterpause umgeht die Alias-Prüfung wie die normale Domain-Prüfung.

## Prüfungen

82 JVM-Tests bestanden, Build und Android Lint erfolgreich. Neue Fälle prüfen direkte und mehrstufige Tracker-Aliase über UDP/TCP, normale CDN-Aliase, Erlaubnisvorrang, bereits gefüllte Caches, fremde Antwortnamen und Filterpause.

OISD-Prüfungen messen die zusätzliche Domainabdeckung, prüfen weitere 600 DNS-Kombinationen für 100 neue Namen sowie Grenzen/Subdomains für 1.000 neue Namen, und lehnen ungültige oder unvollständige Dateien ab. Die gemeinsam gespeicherte dritte Quelle und ihre Fehlerbehandlung werden auf Android geprüft.

Der reale TUN-Test enthält jetzt zusätzlich einen scheinbar eigenen Testnamen, dessen CNAME auf `doubleclick.net` verweist. Er wird über UDP und TCP an synthetischen DNS-Adressen und festen Google-DNS-Adressen über IPv4 und IPv6 geprüft. Die Netzwerktests verwenden kontrollierte Antworten; sie sind kein vorgetäuschter Live-Quad9- oder Live-Turtlecute-Test.

## Grenzen

Kein allgemeiner Schutz vor sämtlichem First-Party-Tracking: ohne sichtbaren CNAME und ohne passende Sperrregel kann die Prüfung nicht greifen. Andere Alias-Verfahren und Inhaltsdomain-Werbung bleiben Grenzen. Für Seitenelemente ist ein Browser-Inhaltsblocker zusätzlich sinnvoll. Unbekannte Resolver sind nicht vollständig erfasst. Größere Filter können App-Funktionen beeinträchtigen; Erlaubnisse, App-Ausnahmen und Standardmodus bleiben verfügbar. Echte Akku-/Langzeitmessungen auf dem Nutzertelefon stehen aus.

Der endgültige Android-10-Lauf bestand alle 10 Tests in 223,297 Sekunden, einschließlich neuer Alias-Anfragen, In-App-Schutztest, Statistik-Stresstest, Snapshot-/Updatefehlern, nativer Socket Protection, Resolver-Port-Sperre und Standardmodus-Rückwechsel. Build/Lint erfolgreich; APK-Signatur und 16-KiB-ZIP-Alignment geprüft.
