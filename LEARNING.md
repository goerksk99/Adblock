# AdShield einfach erklärt

## Was ist Kotlin?

Kotlin ist die Programmiersprache der App. Sie beschreibt beispielsweise: „Wenn du diese Domain erlaubt hast, leite die Anfrage weiter.“ Jetpack Compose baut die sichtbaren Bildschirme aus diesem Programmcode. Material 3 liefert die grundlegenden Android-Bedienelemente und Farben.

## Was ist DNS?

Eine Internetadresse wie `example.com` ist für Menschen lesbar. Zum Verbinden braucht dein Gerät normalerweise eine IP-Adresse, etwa wie ein Telefonbuch eine Nummer zu einem Namen liefert. DNS ist dieses verteilte Internet-Telefonbuch. Eine Seite kann außer ihrer eigenen Adresse viele weitere Domains für Bilder, Videos, Werbung und Tracking abfragen.

## Wie wird Werbung blockiert?

Wenn eine Domain auf der lokalen Sperrliste steht, beantwortet AdShield die DNS-Frage mit „diese Domain existiert nicht“ (NXDOMAIN). Die App oder Seite kann diese Adresse dann normalerweise nicht laden. Es wird kein sichtbares Werbebild nachträglich aus einer Seite entfernt. Deshalb bleiben beispielsweise manche leeren Werbeflächen oder Pop-ups bestehen.

Eine Ausnahme für `example.com` erlaubt auch `login.example.com`. Sie erlaubt nicht automatisch einen anderen Anbieter wie `ads.other-company.com`, den eine Webseite zusätzlich benutzt. Deine erlaubten Domains haben Vorrang vor eigenen und vorinstallierten lokalen Sperren. Quad9 kann eine Domain unabhängig davon wegen bekannter Bedrohungen ablehnen.

## Was ist VpnService? Warum ohne Root?

Android bietet Apps eine offizielle VPN-Schnittstelle. Du musst diese einmal ausdrücklich im Systemdialog freigeben. AdShield richtet darüber nur zwei besondere DNS-Adressen ein. Android leitet Anfragen an diese Adressen in die App. Die App liest echte Netzwerkpakete, beantwortet DNS und schreibt passende Antwortpakete zurück.

Dafür braucht sie keine Administratorrechte („Root“), weil du diese eng begrenzte Android-VPN-Funktion genehmigst. Es gibt keinen entfernten AdShield-VPN-Server. Normaler Webseiten- und Videoverkehr läuft direkt über deine bestehende Verbindung. Das VPN-Schlüsselsymbol bedeutet deshalb nicht, dass dein gesamter Internetverkehr anonymisiert wird.

## Was passiert beim Aktivieren?

1. Die App erklärt die VPN-Freigabe und die Übermittlung erlaubter DNS-Fragen an Quad9.
2. Android fragt dich um Zustimmung, falls sie noch fehlt.
3. AdShield lädt seine gültigen Regeln und prüft, ob Quad9 eine normale Domain beantworten kann.
4. Der lokale Filter wird geprüft, danach wird die DNS-Schnittstelle eingerichtet.
5. Bei Erfolg erscheint „Schutz aktiv“. Bei einem Fehler erscheint eine Fehlermeldung und die Verbindung wird freigegeben, soweit Androids VPN-Lockdown dies nicht verhindert.

Der Status ist keine Garantie, dass jede einzelne App ihren eigenen verschlüsselten DNS-Dienst nicht umgeht. Deshalb zeigt das Dashboard echte verarbeitete Anfragen statt erfundener Erfolgszahlen.

## Wo ist mein Verlauf?

Es gibt keinen Domainverlauf und keine Cloud-Analyse. Die App speichert nur, wie viele Anfragen pro Tag erlaubt, blockiert oder fehlerhaft waren. „50 blockierte Anfragen“ bedeutet nicht automatisch „50 Werbeanzeigen verhindert“: Eine Anzeige kann mehrere Anfragen verursachen, manche Abfragen werden wiederholt und manche betreffen Tracking statt sichtbarer Werbung.

## Was ist Always-on?

Android kann einen freigegebenen VPN-Dienst im Hintergrund wieder starten. Das ist zuverlässiger als ein eigener Autostart-Trick. Aktiviere bei AdShield „Durchgehend aktives VPN“ erst nach dem ersten Funktionstest. Lass **„Verbindungen ohne VPN blockieren“ ausgeschaltet**, denn diese Funktion passt nicht zu unserer gezielten DNS-only-Weiterleitung.

Wenn Always-on an ist, pausiert der App-Ausschalter den lokalen Filter; die DNS-Verbindung zu Quad9 bleibt erhalten und ist so beschriftet. Zum vollständigen Trennen Always-on in den Android-Einstellungen ausschalten und danach den App-Schalter ausschalten.

## Wo liegt welcher Code?

Alle folgenden Pfade beziehen sich auf `app/src/main/java/de/adshield/`:

* `MainActivity.kt`: Bildschirme, Android-Freigabe und Navigation.
* `ui/ShieldModel.kt`: verbindet Bildschirme mit Einstellungen und Dienst.
* `vpn/DnsVpnService.kt`: Android-VPN, Lebenszyklus und Paketverteilung.
* `vpn/Quad9.kt`: verschlüsselter DNS-Upstream, geschützte Sockets.
* `core/DomainRules.kt`: Domainregeln und Listenparser.
* `core/DnsEngine.kt`: DNS-Antworten, Sperren und Cache.
* `core/IpPacket.kt`, `core/TcpDnsServer.kt`: echte IP-/UDP-/TCP-Pakete.
* `data/AppStore.kt`: Einstellungen und Datenbank für Tagesstatistiken.
* `data/FilterRepository.kt`, `data/FilterWorker.kt`: sichere Filterübernahme und Hintergrundupdates.

Automatische Protokolltests liegen unter `app/src/test/`, Android-Datenhaltungstests unter `app/src/androidTest/`. `TESTING.md` unterscheidet ausgeführte Tests und noch offene Smartphone-Prüfungen.
