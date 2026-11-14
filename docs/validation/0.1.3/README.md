# AdShield 0.1.3 – gezielter Schutz gegen bekannte DNS-Umgehungen

Neu gegenüber 0.1.2: Der Maximalmodus kann ausgewählte feste öffentliche DNS-Adressen erfassen. Es werden 20 exakte Hostrouten eingerichtet (10 IPv4, 10 IPv6), keine Standardrouten oder gemeinsamen CDN-Netze. UDP-/TCP-DNS auf Port 53 wird vom lokalen Filter beantwortet. Nicht-DNS-Verkehr an diesen Resolver-IPs wird verworfen, insbesondere Verbindungen für DoH/DoT/QUIC. Keine TLS-Entschlüsselung. Ausgewählte Resolver-Hostnamen werden zusätzlich lokal blockiert. Der separate Schalter entfernt bei Bedarf diesen zusätzlichen Schutz; der Standardmodus und Always-on-Pause erhalten keine zusätzlichen Routen. Eigene Quad9-Sockets bleiben geschützt und verwenden feste Bootstrap-Adressen.

## Nachweise

- 67 JVM-Tests bestanden, 0 Fehler: bisherige 43 Screenshot-Domains, exakte Resolver-IP-Grenzen, optionale Resolver-Hostnamen, Erlaubnisvorrang, Erhalt von Resolver-IP/Ports in UDP-Antworten für alle 20 Adressen.
- Build und Android Lint erfolgreich.
- Vollständiger Android-10-Lauf: 9 Tests bestanden in 102,713 Sekunden. Der reale TUN-Test fragt zusätzlich `8.8.8.8` und `2001:4860:4860::8888` direkt über UDP und TCP ab. Normale Domains werden beantwortet, eine Werbedomain wird blockiert. Die zusätzlichen Hostrouten sind vorhanden; eine Standardroute ist weiterhin ausgeschlossen.
- Nach kontrolliertem Resolver-Ausfall wird das VPN freigegeben. Ein anschließender Start im Standardmodus entfernt die zusätzlichen Resolver-Routen.
- Android-Paketpfad verwendet einen definierten Testresolver; kein vorgetäuschter Live-Quad9-Test vom Smartphone.

## Grenzen und Bedienung

Festes verschlüsseltes DNS in Browsern oder Android kann durch diesen Schutz ausfallen. Dann den zusätzlichen Schalter ausschalten oder im Browser das System-DNS nutzen. Ein erlaubter Resolver-Hostname öffnet die Ports an erfassten Resolver-IPs nicht automatisch. App-Ausnahmen bleiben möglich. Verworfenes Nicht-DNS wird nicht als blockierte DNS-Anfrage in den Tageszähler umgedeutet.

Unbekannte, private, eigene oder anderweitig erreichbare Resolver sind nicht vollständig erfasst. Werbung aus Inhaltsdomains, feste Inhalts-IP-Adressen und Seitenelemente bleiben DNS-Grenzen. Ein zusätzlicher Browser-Inhaltsblocker kann deshalb weiterhin mehr entfernen. Kein vollständiger Live-Turtlecute-Test, keine Freigabe auf dem Nutzertelefon. Die 326.947 Domainlistenregeln aus 0.1.2 bleiben erhalten.

Gezielter Zusatzlauf des erweiterten Pakettests: bestanden in 39,865 Sekunden. Ein UDP-Paket an Port 443 von `8.8.8.8` erreicht nachweislich die reale TUN-Schnittstelle und erhält keine DNS-Antwort; der Test endet mit dem erwarteten Timeout. Dies ist kein vollständiger TLS-/DoH-Handshake-Test. Danach bestätigt derselbe Lauf erneut den Rückwechsel auf Standardrouten.
