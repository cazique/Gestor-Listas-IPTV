package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Contenido de `update.json`, publicado en cada Release (sección 6.4.3). */
@Serializable
data class InfoActualizacion(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val minSdk: Int = 1,
    val releaseNotes: String = "",
    val publishedAt: String = "",
)

sealed interface ResultadoComprobacion {
    data class Disponible(val info: InfoActualizacion, val preliminar: Boolean) : ResultadoComprobacion
    data class AlDia(val ultima: Long?) : ResultadoComprobacion
    data class Incompatible(val info: InfoActualizacion, val sdkDispositivo: Int) : ResultadoComprobacion
    data class Error(val mensaje: String) : ResultadoComprobacion
}

/** Guarda ETag y cuerpo de la última respuesta para hacer peticiones condicionales. */
interface CacheHttp {
    fun leer(url: String): Pair<String, String>?
    fun guardar(url: String, etag: String, cuerpo: String)
}

class CacheMemoria : CacheHttp {
    private val m = HashMap<String, Pair<String, String>>()
    override fun leer(url: String) = m[url]
    override fun guardar(url: String, etag: String, cuerpo: String) { m[url] = etag to cuerpo }
}

/**
 * Política de red de la autoactualización: solo HTTPS y solo los dominios de GitHub necesarios
 * (API, página de descarga y almacenamiento de los archivos de los Releases).
 */
data class PoliticaRed(
    val hosts: Set<String> = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
    val soloHttps: Boolean = true,
) {
    fun permite(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        if (soloHttps && !u.isHttps) return false
        return u.host in hosts
    }
}

/** Comprueba si hay una versión nueva en los Releases de GitHub (sección 6.4.4). */
class ComprobadorActualizaciones(
    private val http: OkHttpClient,
    private val repositorio: String,
    private val cache: CacheHttp = CacheMemoria(),
    private val apiBase: String = "https://api.github.com",
    private val politica: PoliticaRed = PoliticaRed(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun comprobar(versionActual: Long, sdkDispositivo: Int, incluirPreliminares: Boolean = false): ResultadoComprobacion = try {
        val release = if (incluirPreliminares) {
            (json.parseToJsonElement(getApi("$apiBase/repos/$repositorio/releases?per_page=10")) as JsonArray)
                .map { it.jsonObject }
                .firstOrNull { it["draft"]?.jsonPrimitive?.boolean != true }
        } else {
            json.parseToJsonElement(getApi("$apiBase/repos/$repositorio/releases/latest")).jsonObject
        }
        if (release == null) ResultadoComprobacion.AlDia(null) else evaluar(release, versionActual, sdkDispositivo)
    } catch (e: SinReleases) {
        ResultadoComprobacion.AlDia(null)
    } catch (e: Exception) {
        ResultadoComprobacion.Error(e.message ?: e.javaClass.simpleName)
    }

    private fun evaluar(release: JsonObject, versionActual: Long, sdk: Int): ResultadoComprobacion {
        val preliminar = release["prerelease"]?.jsonPrimitive?.boolean == true
        val assets = release["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
        val urlUpdate = assets.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == "update.json" }
            ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            ?: return ResultadoComprobacion.Error("El Release no tiene update.json")
        val info = json.decodeFromString(InfoActualizacion.serializer(), get(urlUpdate))
        if (!politica.permite(info.apkUrl)) return ResultadoComprobacion.Error("Dirección del APK no permitida")
        if (!Regex("^[0-9a-fA-F]{64}$").matches(info.sha256)) return ResultadoComprobacion.Error("SHA-256 no válido en update.json")
        return when {
            info.versionCode <= versionActual -> ResultadoComprobacion.AlDia(info.versionCode)
            info.minSdk > sdk -> ResultadoComprobacion.Incompatible(info, sdk)
            else -> ResultadoComprobacion.Disponible(info, preliminar)
        }
    }

    private class SinReleases : IOException("Sin Releases")

    private fun getApi(url: String): String {
        if (!politica.permite(url)) throw IOException("Dirección no permitida")
        val previo = cache.leer(url)
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { previo?.let { header("If-None-Match", it.first) } }
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 304 && previo != null) return previo.second
            if (r.code == 404) throw SinReleases()
            if (!r.isSuccessful) throw IOException("GitHub respondió ${r.code}")
            val cuerpo = r.body.string()
            r.header("ETag")?.let { cache.guardar(url, it, cuerpo) }
            return cuerpo
        }
    }

    private fun get(url: String): String {
        if (!politica.permite(url)) throw IOException("Dirección no permitida")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!politica.permite(r.request.url.toString())) throw IOException("Redirección no permitida")
            if (!r.isSuccessful) throw IOException("Descarga de update.json: ${r.code}")
            return r.body.string()
        }
    }
}

class ErrorActualizacion(mensaje: String) : IOException(mensaje)

/** Descarga el APK con reintentos y comprobación de espacio, y verifica su SHA-256. */
class DescargadorApk(
    private val http: OkHttpClient,
    private val politica: PoliticaRed = PoliticaRed(),
    private val reintentos: Int = 3,
    private val esperaMs: Long = 2000,
) {
    fun descargar(info: InfoActualizacion, destino: File, progreso: (Long, Long) -> Unit = { _, _ -> }): File {
        if (!politica.permite(info.apkUrl)) throw ErrorActualizacion("Dirección del APK no permitida")
        var ultimo: Exception? = null
        repeat(reintentos) { intento ->
            try {
                descargarUnaVez(info, destino, progreso)
                val hash = sha256(destino)
                if (!hash.equals(info.sha256, ignoreCase = true)) {
                    destino.delete()
                    throw ErrorActualizacion("El SHA-256 del APK no coincide con update.json")
                }
                return destino
            } catch (e: ErrorActualizacion) {
                throw e
            } catch (e: Exception) {
                ultimo = e
                destino.delete()
                if (intento < reintentos - 1) Thread.sleep(esperaMs * (intento + 1))
            }
        }
        throw ErrorActualizacion("No se pudo descargar el APK: ${ultimo?.message}")
    }

    private fun descargarUnaVez(info: InfoActualizacion, destino: File, progreso: (Long, Long) -> Unit) {
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!politica.permite(r.request.url.toString())) throw ErrorActualizacion("Redirección no permitida")
            if (!r.isSuccessful) throw IOException("Descarga del APK: ${r.code}")
            val total = r.body.contentLength()
            destino.parentFile?.mkdirs()
            val libre = destino.parentFile?.usableSpace ?: Long.MAX_VALUE
            if (total > 0 && libre < total * 2) throw ErrorActualizacion("No hay espacio libre suficiente")
            r.body.byteStream().use { input ->
                destino.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var leido = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        leido += n
                        progreso(leido, total)
                    }
                }
            }
        }
    }

    companion object {
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { i ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** La actualización solo se instala si el APK lleva exactamente los mismos certificados que la app instalada. */
object VerificacionFirma {
    fun misma(certificadosApk: Set<String>, certificadosInstalada: Set<String>): Boolean =
        certificadosApk.isNotEmpty() && certificadosApk.map { it.lowercase() }.toSet() == certificadosInstalada.map { it.lowercase() }.toSet()
}
