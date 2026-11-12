# AdShield für iPhone und iPad

Eigenständige SwiftUI-App mit lokalem Packet-Tunnel-DNS-Filter für iOS 16 oder neuer. Dies ist eine **Entwicklungsfassung als Quellprojekt**, noch keine signierte, auf einem iPhone geprüfte IPA. Die Android-App bleibt separat.

## Enthalten

- Lokaler systemweiter DNS-Tunnel über `NEPacketTunnelProvider`, DNS über UDP/TCP und IPv4/IPv6.
- Ausschließlich Routen zu den virtuellen DNS-Adressen `10.111.0.2/32` und `fdad:51e1::2/128`; keine Internet-Standardroute und kein eigener VPN-Server.
- HTTPS-DNS zu `dns.quad9.net` mit unveränderter Apple-Zertifikatsprüfung, ohne unverschlüsselten Ersatzresolver. Vor erfolgreichem Start muss eine echte DNS-Probe beantwortet werden.
- 78.215 mitgelieferte StevenBlack-Filterdomains, lokale NXDOMAIN-Blockierung, manuell aktualisierbare und validierte Hosts-Datei, atomarer Austausch.
- Erlaubnis- und Sperrlisten einschließlich Subdomains. Erlaubnisse haben Vorrang. Internationale Namen als Punycode.
- Aktivierung, Tagesstatistiken, Filterpause, Ausnahmen und Einstellungen auf Deutsch. App-Group-Dateien verbinden App und Extension; keine Domain-Verlaufsprotokolle, Telemetrie oder Konten.
- TCP mit begrenzten Verbindungen/Puffern, Segmentierung nach MSS, Wiederholungen und Zeitlimits. Große UDP-Antworten erzwingen TCP; ohne EDNS gilt das 512-Byte-Limit, mit EDNS maximal 1232 Bytes.

## Auf einem Mac vorbereiten

Benötigt werden macOS, Xcode 16 oder neuer mit iOS SDK und XcodeGen (mindestens 2.42). XcodeGen erzeugt das Xcode-Projekt aus der versionierten `project.yml`.

```sh
brew install xcodegen
cd ios
./scripts/build-macos.sh
open AdShield.xcodeproj
```

Das Skript führt die Swift-Kerntests und einen **unsignierten** Geräte-Build aus. Es installiert oder veröffentlicht nichts. Linux kann den Swift-Kern testen, unterstützt aber kein Xcode/iPhone-SDK.

## Auf dem eigenen iPhone installieren

1. In Xcode das Projekt öffnen. Ein eigenes, eindeutiges `ADSHIELD_BUNDLE_PREFIX` setzen, beispielsweise `de.meinname.adshield`. Daraus entstehen App-ID, Extension-ID (`.Tunnel`) und gemeinsame Gruppe (`group.` + App-ID). Beide Targets müssen dieselben Werte verwenden.
2. Für **beide Targets** unter „Signing & Capabilities“ dasselbe bezahlte Apple-Developer-Team wählen. Automatic Signing aktivieren. Die Capabilities **Network Extensions / Packet Tunnel** und **App Groups** sowie die gemeinsame Gruppe im Developer-Portal für beide IDs aktivieren. Profile müssen die in `project.yml` erzeugten Entitlements enthalten. Keine Zugangsdaten ins Projekt oder Chat schreiben.
3. Verbundenes iPhone als Ziel und Scheme **AdShield** wählen. Developer Mode auf dem Gerät aktivieren, dann „Run“. Der Simulator ersetzt keinen echten Network-Extension-Test.
4. AdShield öffnen, „Aktivieren“ wählen und Apples VPN-Konfiguration erlauben. Ein externer VPN-Anbieter ist nicht erforderlich. Andere aktive VPNs können kollidieren.
5. Für TestFlight in Xcode „Product → Archive“, dann Organizer → „Distribute App → App Store Connect“ verwenden. Dafür sind ein App-Eintrag, eindeutige IDs, passende Profile, Icons, Datenschutzangaben und Apples Prüfung erforderlich. Es wurde hier nichts bei Apple hochgeladen.

Für eine private Signierung gelten Apples Team- und Geräteanforderungen. Ein kostenloses Personal Team reicht für diese Network-Extension-Capabilities nicht zuverlässig aus. Ein App-Store-Angebot als VPN-App unterliegt zusätzlichen Apple-Vorgaben; die Freigabe ist nicht durch dieses Projekt garantiert.

## Schutzumfang und Grenzen

DNS-Filter arbeiten in Apps und Browsern, wenn diese das System-DNS verwenden. Eigene DoH-Resolver, feste IP-Adressen und Werbung von derselben Domain wie der Inhalt umgehen Domainfilter. Keine Zusage für YouTube-/Instagram-/TikTok-Werbeblockierung. Apple erlaubt privaten Apps weder eine allgemeine Liste aller installierten Apps noch beliebige Per-App-VPN-Ausnahmen. Deshalb gibt es keine irreführende „Apps“-Liste.

Die App ist kein Anonymisierungs-VPN und kein Kill Switch. Filterupdates laufen manuell. Statistiken sind lokale Tageszähler, keine langfristige Verlaufssammlung. Geblockte Domains werden nicht an Quad9 gesendet; erlaubte DNS-Abfragen gehen an Quad9. Updates gehen an GitHub. Link zur Quad9-Datenschutzrichtlinie steht in der App. Filterlizenzen befinden sich in `Resources/`.

IP-Fragmente, IPv6-Erweiterungsheader und komprimierte DNS-Fragen werden verworfen. Normale lokale DNS-Abfragen passen in die MTU; umfangreiche Antworten werden per TCP transportiert. Die Portierung umfasst keinen allgemeinen IP-/HTTP-Proxy. Nicht als durchgehend getestete iOS-Netzwerkimplementierung ausgeben.

## Validierung und noch nötige Geräteprüfung

Siehe [VALIDATION.md](VALIDATION.md). Linux-Prüfungen ersetzen weder einen vollständigen Xcode-Build noch echte iPhone-Tests. Erst nach erfolgreicher Apple-Signierung und Geräteprüfung kann eine installierbare Version ausgeliefert werden.
