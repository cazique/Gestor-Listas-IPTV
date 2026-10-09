package es.cazique.iptvgestor.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class ErrorProveedor(mensaje: String, val codigo: Int? = null) : IOException(mensaje)

/**
 * Cliente de `player_api.php` (sección 3.2). No usa `get.php` (bloqueado por el CDN del proveedor).
 * Ningún mensaje de error incluye usuario ni contraseña.
 */
class ClienteXtream(
    private val http: OkHttpClient,
    private val cuenta: Cuenta,
    private val userAgent: String = "VLC/3.0.20",
) {
    private fun url(accion: String?, extra: Map<String, String> = emptyMap()): HttpUrl {
        val base = "${cuenta.base}/player_api.php".toHttpUrlOrNull() ?: throw ErrorProveedor("Dirección del servidor no válida")
        return base.newBuilder()
            .addQueryParameter("username", cuenta.usuario)
            .addQueryParameter("password", cuenta.contrasena)
            .apply { accion?.let { addQueryParameter("action", it) } }
            .apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
    }

    fun texto(accion: String?): String {
        val req = Request.Builder().url(url(accion)).header("User-Agent", userAgent).build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw ErrorProveedor("El servidor respondió ${r.code}", r.code)
                return r.body.string()
            }
        } catch (e: ErrorProveedor) {
            throw e
        } catch (e: IOException) {
            throw ErrorProveedor(Redactor.redactar(describir(e), cuenta))
        }
    }

    fun estado(): EstadoCuenta = EstadoCuenta.desdeJson(texto(null))
    fun categorias(): List<Categoria> = XtreamJson.categorias(texto("get_live_categories"))
    fun streams(): List<Stream> = XtreamJson.streams(texto("get_live_streams"))
    fun vodCategorias(): String = texto("get_vod_categories")
    fun vodStreams(): String = texto("get_vod_streams")
    fun seriesCategorias(): String = texto("get_series_categories")
    fun series(): String = texto("get_series")

    fun infoSerie(id: Long): String {
        val req = Request.Builder().url(url("get_series_info", mapOf("series_id" to id.toString()))).header("User-Agent", userAgent).build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw ErrorProveedor("El servidor respondió ${r.code}", r.code)
                return r.body.string()
            }
        } catch (e: ErrorProveedor) { throw e } catch (e: IOException) { throw ErrorProveedor(Redactor.redactar(describir(e), cuenta)) }
    }

    companion object {
        fun describir(e: Throwable): String = when (e) {
            is UnknownHostException -> "No se encuentra el servidor"
            is ConnectException -> "Conexión rechazada"
            is SocketTimeoutException -> "Tiempo de espera agotado"
            is SSLHandshakeException, is SSLPeerUnverifiedException -> "Error de certificado TLS"
            is SSLException -> "Error de TLS"
            else -> e.javaClass.simpleName
        }
    }
}

enum class ResultadoHttps { OK, ERROR_CERTIFICADO, NO_DISPONIBLE }

/**
 * Política HTTPS primero (sección 9): nunca se degrada a HTTP ante un error de certificado;
 * solo si el servidor no ofrece HTTPS, y entonces hace falta consentimiento explícito por host.
 */
object PoliticaHttps {
    fun variantes(host: String, httpsPort: Int? = null): Pair<String, String> {
        val h = host.trim().trimEnd('/').removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        val sinPuerto = h.substringBefore(':').substringBefore('/')
        val https = if (httpsPort != null && httpsPort != 443) "https://$sinPuerto:$httpsPort" else "https://$sinPuerto"
        return https to "http://$h"
    }

    fun clasificar(e: Throwable?): ResultadoHttps = when (e) {
        null -> ResultadoHttps.OK
        is SSLHandshakeException, is SSLPeerUnverifiedException -> ResultadoHttps.ERROR_CERTIFICADO
        is ErrorProveedor -> if (e.message?.contains("certificado") == true) ResultadoHttps.ERROR_CERTIFICADO else ResultadoHttps.NO_DISPONIBLE
        else -> ResultadoHttps.NO_DISPONIBLE
    }

    fun clave(host: String): String = host.trim().lowercase().removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
}
