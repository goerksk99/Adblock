# Android-Prüfung vom 14. November 2026

Untersucht wurde die vorhandene Android-Version 0.1.1. Es wurde keine geänderte APK ausgeliefert und keine Filterregel stillschweigend ergänzt.

## Ergebnisse

- 60 JVM-Tests bestanden, 0 Fehler. Android Lint erfolgreich.
- 9 Android-10-Instrumentierungstests bestanden (25,952 Sekunden): Speicherung, echte Socket Protection über IPv4/IPv6 und TUN-DNS-Paketpfad. Der Paketpfadtest nutzt einen kontrollierten Testresolver; er behauptet keinen echten Quad9-Verkehr vom Smartphone.
- 43 vollständig lesbare Domains aus den Nutzer-Screenshots gegen die ausgelieferte StevenBlack-Liste geprüft: 27 werden durch vorhandene Regeln erfasst, 16 nicht.
- Zwei neue Regressionstests verwenden den tatsächlichen `HostsParser`, `FilterEngine` und `DnsEngine`. 129 Fälle (43 Namen × A/AAAA/HTTPS) prüfen die jetzige Abdeckung und dass blockierte Anfragen den Test-Upstream nicht erreichen.
- Weitere 258 Fälle (43 Namen × drei DNS-Typen × UDP/TCP) prüfen, dass ausdrückliche Sperrregeln alle gemeldeten Lücken schließen können. Der Test-Upstream darf dabei überhaupt nicht aufgerufen werden.
- Zwei echte Quad9-DoH-Anfragen vom Cloud-Rechner beantworteten `example.com` erfolgreich mit IPv4- und IPv6-Adressen. Dies ist kein Android-VPN-Test und kein Test der Internetverbindung des Nutzers.

## Einordnung der Screenshots

Die 27 vorhandenen Sperren passen zu den grünen Häkchen, die 16 nicht abgedeckten Namen zu den roten Kreuzen auf den übermittelten Bildern. Grün ist hier also kein Nachweis, dass der Filter versagt. Die Testseite selbst bleibt durch die Cloud-Netzwerkregeln nicht erreichbar; ihre Legende wurde deshalb nicht unabhängig im Live-Browser bestätigt.

Fehlende Regeln beweisen keinen Fehler des VPNs. Manche Namen gehören auch zu Service-APIs, Verwaltungsoberflächen oder Fehlerberichten. Deshalb werden keine kompletten Anbieter-Domains blind gesperrt. Der Test belegt lokale Regelverarbeitung, aber nicht, dass der Browser auf dem Nutzergerät tatsächlich jedes DNS-Paket durch den VPN sendet. Eigenes Browser-DoH, Ausnahmen und bestehende Caches sind separat am Gerät zu prüfen.

## Abdeckung der Namen

| Domain | Durch mitgelieferte Regeln erfasst | Passende Regel |
|---|---|---|
| `freshmarketer.com` | Nein | `—` |
| `claritybt.freshmarketer.com` | Nein | `—` |
| `fwtracks.freshmarketer.com` | Nein | `—` |
| `stats.wp.com` | Ja | `stats.wp.com` |
| `notify.bugsnag.com` | Ja | `notify.bugsnag.com` |
| `sessions.bugsnag.com` | Ja | `sessions.bugsnag.com` |
| `api.bugsnag.com` | Nein | `—` |
| `app.bugsnag.com` | Nein | `—` |
| `events.reddit.com` | Nein | `—` |
| `events.redditmedia.com` | Ja | `events.redditmedia.com` |
| `ads-api.tiktok.com` | Ja | `ads-api.tiktok.com` |
| `analytics.tiktok.com` | Ja | `analytics.tiktok.com` |
| `ads-sg.tiktok.com` | Ja | `ads-sg.tiktok.com` |
| `analytics-sg.tiktok.com` | Ja | `analytics-sg.tiktok.com` |
| `business-api.tiktok.com` | Nein | `—` |
| `ads.tiktok.com` | Ja | `ads.tiktok.com` |
| `log.byteoversea.com` | Nein | `—` |
| `ads.yahoo.com` | Ja | `ads.yahoo.com` |
| `analytics.yahoo.com` | Ja | `analytics.yahoo.com` |
| `geo.yahoo.com` | Ja | `geo.yahoo.com` |
| `udcm.yahoo.com` | Nein | `—` |
| `analytics.query.yahoo.com` | Ja | `analytics.query.yahoo.com` |
| `partnerads.ysm.yahoo.com` | Ja | `partnerads.ysm.yahoo.com` |
| `log.fc.yahoo.com` | Nein | `—` |
| `gemini.yahoo.com` | Ja | `gemini.yahoo.com` |
| `adtech.yahooinc.com` | Ja | `adtech.yahooinc.com` |
| `auction.unityads.unity3d.com` | Ja | `auction.unityads.unity3d.com` |
| `adserver.unityads.unity3d.com` | Ja | `adserver.unityads.unity3d.com` |
| `extmaps-api.yandex.net` | Nein | `—` |
| `appmetrica.yandex.ru` | Nein | `—` |
| `adfstat.yandex.ru` | Ja | `adfstat.yandex.ru` |
| `metrika.yandex.ru` | Nein | `—` |
| `offerwall.yandex.net` | Nein | `—` |
| `adfox.yandex.ru` | Ja | `adfox.yandex.ru` |
| `iot-eu-logser.realme.com` | Ja | `iot-eu-logser.realme.com` |
| `iot-logser.realme.com` | Ja | `iot-logser.realme.com` |
| `iadsdk.apple.com` | Ja | `iadsdk.apple.com` |
| `metrics.icloud.com` | Ja | `metrics.icloud.com` |
| `metrics.mzstatic.com` | Ja | `metrics.mzstatic.com` |
| `api-adservices.apple.com` | Nein | `—` |
| `api.ad.xiaomi.com` | Ja | `api.ad.xiaomi.com` |
| `data.mistat.xiaomi.com` | Ja | `data.mistat.xiaomi.com` |
| `data.mistat.india.xiaomi.com` | Nein | `—` |

## Unterbrochener erster Android-Lauf

Ein erster Lauf absolvierte Speicherung und Socket-Tests, wurde aber während des VPN-Tests beendet. Logcat nennt ausdrücklich `Force stopping de.adshield ... installPackageLI`: Eine vorher angestoßene Neuinstallation wurde nach rund 216 Sekunden Hintergrund-Dexoptimierung verspätet fertig und beendete den Testprozess. Der Lauf wurde nach abgeschlossener Installation ohne Codeänderung wiederholt und bestand vollständig. Das unterbrochene Protokoll ist erhalten; es wird nicht als bestandener Test gezählt. Die getestete installierte App meldet Version 0.1.1, versionCode 2.
