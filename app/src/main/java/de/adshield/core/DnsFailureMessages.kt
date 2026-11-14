package de.adshield.core

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class DnsHttpException(val statusCode:Int):IOException("Quad9 HTTP $statusCode")

/** Fixed, actionable messages. Never display or persist query names or exception text. */
object DnsFailureMessages {
    fun describe(error:Exception?):String {
        val causes=generateSequence(error as Throwable?){it.cause}.take(8).toList()
        return when {
            causes.any{it is SocketProtectionException}->"Android konnte den DNS-Socket nicht vom VPN ausnehmen. Bitte die VPN-Freigabe erneut prüfen."
            causes.any{it is SSLException}->"Die sichere Verbindung zu Quad9 scheitert an der TLS-Zertifikatsprüfung. Bitte Datum, Uhrzeit und mögliche Netzwerkfilter prüfen."
            causes.any{it is DnsHttpException}->"Quad9 antwortet mit HTTP ${(causes.first{it is DnsHttpException} as DnsHttpException).statusCode}. Bitte später erneut versuchen."
            causes.any{it is SocketTimeoutException || it is InterruptedIOException}->"Zeitüberschreitung beim Verbinden mit Quad9. Bitte WLAN oder mobile Daten prüfen und erneut aktivieren."
            causes.any{it is UnknownHostException}->"Der DNS-Resolver konnte nicht aufgelöst werden. Bitte die Netzwerkeinstellungen prüfen."
            causes.any{it is ConnectException}->"Die HTTPS-Verbindung zu Quad9 wurde nicht aufgebaut. Bitte die Verbindung und mögliche Netzwerkfilter prüfen."
            causes.any{it is ProtocolException}->"Quad9 hat keine gültige vollständige DNS-Antwort geliefert. Bitte erneut versuchen."
            else->"Die DNS-Verbindung zu Quad9 ist fehlgeschlagen (${error?.javaClass?.simpleName?:"unbekannter Fehler"}). Bitte erneut versuchen."
        }
    }
}
