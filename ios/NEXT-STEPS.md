# Was du jetzt tun musst

## 1. Den Mac-Build auf GitHub prüfen

Öffne https://github.com/goerksk99/Adblock/actions/workflows/ios-check.yml und den neuesten Lauf.

- **Grün:** Kerntests sowie iOS-Geräte-Build und Simulator-Start wurden erfolgreich ausgeführt. Unter „Artifacts → AdShield-iOS-build“ liegen das unsignierte App-Paket, die Protokolle und ein Screenshot. Zum Herunterladen von GitHub-Artefakten musst du bei GitHub angemeldet sein.
- **Queued:** GitHub hat den Lauf angenommen, aber noch keinen Mac-Runner zugewiesen. Das ist noch kein erfolgreicher Build.
- **Rot:** Öffne den Lauf und sende mir die Meldung unter „Annotations“ oder das Protokoll. Nicht versuchen, die unsignierte App auf dem iPhone zu installieren.
- „Run workflow“ startet einen weiteren Lauf. Es werden keine Apple-Zugangsdaten benötigt und keine Apps zu Apple hochgeladen.

Die Prüfung läuft auf einem GitHub-Mac. Dafür brauchst du keinen eigenen Mac. Die Verfügbarkeit von Actions und Mac-Runnern wird von GitHub verwaltet.

## 2. Apple-Signierung vorbereiten

Für die iPhone-Installation brauchst du ein passendes **Apple-Developer-Team** mit Network-Extension- und App-Group-Capabilities. Ein kostenloses Personal Team ist dafür kein verlässlicher Weg. Anmeldung: https://developer.apple.com/programs/

Du musst mir **kein Passwort, keinen privaten Schlüssel und keine Zertifikatsdatei** schicken. Team-ID und gewählte Bundle-ID sind keine Geheimnisse.

Der einfachste Weg ist einmalig ein eigener, geliehener oder gemieteter Mac mit Xcode. Auf diesem Mac:

```sh
brew install xcodegen
cd AdShield-iOS
./scripts/configure-signing.sh DEINE10ID0 de.goerksk99.adshield
./scripts/build-macos.sh
open AdShield.xcodeproj
```

`DEINE10ID0` ist ein Platzhalter. Ersetze ihn durch die 10-stellige Team-ID aus deinem Apple-Developer-Konto. Falls die Bundle-ID schon vergeben ist, wähle eine eigene eindeutige ID. Das Skript trägt die Werte für beide Targets gemeinsam ein. Die lokale Datei bleibt außerhalb von Git und Downloadarchiven.

In Xcode unter „Signing & Capabilities“ für **AdShield** und **AdShieldTunnel** dasselbe Team verwenden und die gemeinsamen Capabilities bestätigen:

- Network Extensions: Packet Tunnel
- App Group: `group.de.goerksk99.adshield` beziehungsweise `group.` + deine Bundle-ID

Die IDs und die Gruppe müssen im Apple-Developer-Portal sowie in beiden Provisioning-Profilen zusammenpassen. Anschließend eigenes iPhone auswählen und „Run“. Alternativ in Xcode „Product → Archive → Distribute App → App Store Connect“ für TestFlight verwenden. Eine reine CI-Installation ohne eigenen Mac ist ebenfalls möglich, benötigt aber zusätzliche sichere Zertifikats-/Profil- und App-Store-Connect-Konfiguration; sie ist hier noch nicht eingerichtet.

## 3. Ein iPhone für den echten Schutztest organisieren

Das kann auch das Gerät eines Testers sein. Für den ersten Durchlauf:

1. Installieren, AdShield öffnen, „Aktivieren“ drücken und die VPN-Konfiguration erlauben.
2. Eine normale Webseite öffnen; WLAN und Mobilfunk ausprobieren.
3. Unter „Ausnahmen → Blockieren“ `example.com` eintragen. In einer neuen Browser-Sitzung `https://example.com` öffnen. Die Seite soll nach Ablauf vorhandener DNS-Caches nicht erreichbar sein und der Blockierungszähler steigen.
4. Dieselbe Domain zusätzlich erlauben. Sie muss wieder erreichbar sein. Browser-/DNS-Caches können die Änderung verzögern. Danach beide Testregeln entfernen.
5. Schutz aus- und wieder einschalten. Gerät sperren/entsperren und erneut zwischen WLAN und Mobilfunk wechseln.
6. Notieren: iPhone-Modell, iOS-Version, Netz und genaue Fehlermeldung. Keine privaten besuchten Domains oder Kontodaten mitschicken.

Der erste Durchlauf dauert etwa 10–15 Minuten, sofern Installation und Signierung schon fertig sind. Den vollständigen Freigabetest beschreibt VALIDATION.md. Der iOS-Simulator kann die Oberfläche prüfen, aber keinen echten systemweiten DNS-Schutz bestätigen.
