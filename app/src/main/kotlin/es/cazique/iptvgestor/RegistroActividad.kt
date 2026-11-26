package es.cazique.iptvgestor

import android.content.Context
import android.util.Log
import es.cazique.iptvgestor.core.Redactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de actividad visible en la app (Ajustes → Registro de actividad) y compartible.
 * Todo se redacta antes de guardarse: nunca contiene usuario ni contraseña del proveedor.
 * Se conservan las últimas [MAX] líneas, también en un archivo privado para sobrevivir a un cierre.
 */
object RegistroActividad {
    private const val MAX = 1500
    private const val ETIQUETA = "IptvGestor"
    private val lineas = ArrayDeque<String>()
    private val _flujo = MutableStateFlow<List<String>>(emptyList())
    val flujo: StateFlow<List<String>> = _flujo.asStateFlow()
    private var archivo: File? = null
    private val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.forLanguageTag("es-ES"))

    @Synchronized
    fun iniciar(context: Context) {
        archivo = File(context.filesDir, "registro.txt")
        lineas.clear()
        archivo?.takeIf { it.exists() }?.readLines()?.takeLast(MAX)?.forEach { lineas.addLast(it) }
        _flujo.value = lineas.toList()
    }

    fun info(mensaje: String) = anotar("·", mensaje)
    fun aviso(mensaje: String) = anotar("!", mensaje)
    fun error(mensaje: String, e: Throwable? = null) =
        anotar("✗", mensaje + (e?.let { " — ${it.javaClass.simpleName}: ${it.message ?: ""}" } ?: ""))

    @Synchronized
    private fun anotar(nivel: String, mensaje: String) {
        val linea = "${formato.format(Date())} $nivel ${Redactor.redactar(mensaje).replace('\n', ' ')}"
        Log.i(ETIQUETA, linea)
        lineas.addLast(linea)
        while (lineas.size > MAX) lineas.removeFirst()
        _flujo.value = lineas.toList()
        runCatching {
            val f = archivo ?: return@runCatching
            f.appendText(linea + "\n")
            if (f.length() > 400_000) f.writeText(lineas.joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun texto(): String = lineas.joinToString("\n")

    @Synchronized
    fun borrar() {
        lineas.clear()
        _flujo.value = emptyList()
        runCatching { archivo?.writeText("") }
    }

    /** Dirección sin credenciales: esquema, servidor, puerto y ruta; los parámetros solo por nombre. */
    fun url(u: HttpUrl): String {
        val params = u.queryParameterNames.joinToString("&") { n ->
            if (n.equals("username", true) || n.equals("password", true)) "$n=***" else "$n=${u.queryParameter(n)}"
        }
        return Redactor.redactar("${u.scheme}://${u.host}:${u.port}${u.encodedPath}" + if (params.isEmpty()) "" else "?$params")
    }

    /** Interceptor de OkHttp: una línea por petición con el código de respuesta o el error de red. */
    class Interceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val req = chain.request()
            val inicio = System.nanoTime()
            try {
                val r = chain.proceed(req)
                val ms = (System.nanoTime() - inicio) / 1_000_000
                val linea = "HTTP ${r.code} ${req.method} ${url(req.url)} ($ms ms)"
                if (r.isSuccessful || r.code == 304) info(linea) else aviso(linea)
                return r
            } catch (e: IOException) {
                error("Fallo de red ${req.method} ${url(req.url)}", e)
                throw e
            }
        }
    }
}
