package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// ---------------- Comprobación de enlaces (sección 7.3) ----------------

@Serializable
enum class EstadoEnlace { FUNCIONA, LENTO, FALLA }

/** Resultado por variante. Nunca guarda la URL (lleva credenciales). */
@Serializable
data class ResultadoEnlace(
    val streamId: Long,
    val estado: EstadoEnlace,
    val msPrimerDato: Long?,
    val kbps: Long?,
    val bytes: Long,
    val fecha: Long,
    val detalle: String = "",
)

data class OpcionesComprobacion(
    /** Segundos de muestra para medir la tasa de bits. */
    val segundosMuestra: Int = 6,
    /** Espera tras cada prueba antes de consultar `active_cons` (provisional: ver README). */
    val esperaEntrePruebasMs: Long = 10_000,
    val umbralLentoKbps: Long = 1_500,
    val formato: String = "ts",
    val maxBytes: Long = 8L * 1024 * 1024,
)

sealed interface Parada {
    data object ConexionAjena : Parada
    data object Cancelada : Parada
}

/**
 * Prueba los enlaces de uno en uno (nunca en paralelo), respetando la conexión única:
 * antes de cada prueba consulta `active_cons` y, si es mayor que 0, se detiene.
 */
class ComprobadorEnlaces(
    private val http: OkHttpClient,
    private val cliente: ClienteXtream,
    private val cuenta: Cuenta,
    private val userAgent: String,
    private val opciones: OpcionesComprobacion = OpcionesComprobacion(),
    private val reloj: () -> Long = System::currentTimeMillis,
    private val dormir: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun comprobar(
        streamIds: List<Long>,
        cancelado: () -> Boolean = { false },
        alProgresar: (Int, ResultadoEnlace) -> Unit = { _, _ -> },
    ): Pair<List<ResultadoEnlace>, Parada?> {
        val resultados = ArrayList<ResultadoEnlace>()
        for ((i, id) in streamIds.withIndex()) {
            if (cancelado()) return resultados to Parada.Cancelada
            val activas = runCatching { cliente.estado().conexionesActivas }.getOrNull()
            if (activas != null && activas > 0) return resultados to Parada.ConexionAjena
            val r = probar(id)
            resultados.add(r)
            alProgresar(i, r)
            if (i < streamIds.lastIndex) dormir(opciones.esperaEntrePruebasMs)
        }
        return resultados to null
    }

    fun probar(streamId: Long): ResultadoEnlace {
        val url = GeneradorM3u.urlStream(cuenta, streamId, opciones.formato)
        val cliente = http.newBuilder().readTimeout(15, TimeUnit.SECONDS).callTimeout((opciones.segundosMuestra + 20).toLong(), TimeUnit.SECONDS).build()
        val inicio = reloj()
        return try {
            cliente.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { r ->
                if (!r.isSuccessful) return ResultadoEnlace(streamId, EstadoEnlace.FALLA, null, null, 0, inicio, "HTTP ${r.code}")
                val entrada = r.body.byteStream()
                val buf = ByteArray(32 * 1024)
                var primero: Long? = null
                var bytes = 0L
                val fin = inicio + opciones.segundosMuestra * 1000L
                while (reloj() < fin && bytes < opciones.maxBytes) {
                    val n = entrada.read(buf)
                    if (n < 0) break
                    if (primero == null) primero = reloj() - inicio
                    bytes += n
                }
                val segundos = ((reloj() - inicio - (primero ?: 0)).coerceAtLeast(1)) / 1000.0
                val kbps = if (bytes > 0) (bytes * 8 / 1000 / segundos).toLong() else null
                val estado = when {
                    bytes == 0L -> EstadoEnlace.FALLA
                    kbps != null && kbps < opciones.umbralLentoKbps -> EstadoEnlace.LENTO
                    else -> EstadoEnlace.FUNCIONA
                }
                ResultadoEnlace(streamId, estado, primero, kbps, bytes, inicio)
            }
        } catch (e: IOException) {
            ResultadoEnlace(streamId, EstadoEnlace.FALLA, null, null, 0, inicio, ClienteXtream.describir(e))
        }
    }
}

// ---------------- PIN parental (sección 9) ----------------

/** PIN guardado con PBKDF2-HMAC-SHA256 y sal aleatoria: `iteraciones:sal:hash` en base64. */
object Pin {
    private const val ITERACIONES = 120_000

    fun hash(pin: String, sal: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val h = derivar(pin, sal, ITERACIONES)
        val b = Base64.getEncoder()
        return "$ITERACIONES:${b.encodeToString(sal)}:${b.encodeToString(h)}"
    }

    fun verificar(pin: String, guardado: String): Boolean {
        val partes = guardado.split(':')
        if (partes.size != 3) return false
        val d = Base64.getDecoder()
        val h = derivar(pin, d.decode(partes[1]), partes[0].toIntOrNull() ?: return false)
        return MessageDigest.isEqual(h, d.decode(partes[2]))
    }

    private fun derivar(pin: String, sal: ByteArray, it: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), sal, it, 256)).encoded
}

// ---------------- Subida a un servidor propio (sección 7.4) ----------------

/** Subida por HTTP PUT (también vale para WebDAV). Las credenciales de la subida no se registran. */
class Subidor(private val http: OkHttpClient) {
    fun subir(urlBase: String, nombre: String, contenido: String, usuario: String?, contrasena: String?) {
        val url = urlBase.trimEnd('/') + "/" + nombre
        if (!url.startsWith("https://") && !url.startsWith("http://")) throw IOException("Dirección no válida")
        val req = Request.Builder().url(url)
            .put(contenido.toByteArray(Charsets.UTF_8).toRequestBody("audio/x-mpegurl; charset=utf-8".toMediaType()))
            .apply { if (!usuario.isNullOrEmpty()) header("Authorization", Credentials.basic(usuario, contrasena ?: "")) }
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El servidor respondió ${r.code} al subir $nombre")
        }
    }
}
