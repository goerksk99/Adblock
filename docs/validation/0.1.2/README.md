# AdShield 0.1.2 – Maximaler DNS-Schutz

64 JVM-Tests und 9 Android-10-Instrumentierungstests bestanden; Build, Lint, Signatur und 16-KiB-ZIP-Alignment geprüft. Gleiche Signatur wie 0.1.1, versionCode 3: Installation als Update ohne Deinstallation.

StevenBlack (78.215 Namen) + HaGeZi Ultimate (272.267, Stand 14.11.2026) + gezielte Screenshot-Regeln ergeben 326.947 eindeutige Regeln. Im voreingeschalteten Maximalmodus werden alle 43 vollständig lesbaren Screenshot-Domains durch lokale NXDOMAIN-Antworten blockiert, geprüft für A, AAAA und HTTPS über UDP und TCP (258 Kombinationen). Gesperrte Anfragen dürfen den Test-Upstream nicht erreichen. Erlaubnisregeln haben Vorrang, normale Inhaltsdomains und Label-Grenzen werden separat geprüft.

Android-Tests prüfen zusätzlich den gespeicherten Modusschalter, seine Auswirkung auf die aktive FilterEngine, das Zurückweisen fehlerhafter Downloads beider Quellen ohne Verlust der letzten Listen und die Wiederherstellung aus dem gemeinsam gespeicherten Snapshot. Echte native Socket-Tests über IPv4/IPv6 und der reale TUN-Paketpfad mit kontrolliertem Resolver bestanden. Gesamtlauf: 109,172 Sekunden.

HaGeZi Ultimate ist bewusst aggressiv und kann Anbieter-Dashboards, Telemetrie-abhängige Funktionen oder andere App-Funktionen einschränken. Standardmodus, Domain-Erlaubnisse und App-Ausnahmen bleiben verfügbar. Bei einem Listenupdate werden beide Quellen vor dem atomaren Austausch geprüft. Ältere StevenBlack-Caches bleiben lesbar.

Grenzen: kein Live-Turtlecute-Test (Cloud-Netzwerkregeln), kein Test auf dem Nutzertelefon und kein Beleg, dass der Browser dort das System-DNS nutzt. Eigene DoH-Resolver, feste IP-Adressen und Werbung innerhalb einer Inhaltsdomain bleiben außerhalb der zuverlässigen DNS-Blockierung. Keine TLS-Entschlüsselung und keine Zertifikats-Umgehung.
