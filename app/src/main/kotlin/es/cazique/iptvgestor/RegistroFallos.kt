package es.cazique.iptvgestor

import android.content.Context
import android.os.Build
import es.cazique.iptvgestor.core.Redactor
import java.io.File

/**
 * Guarda el último fallo (con las credenciales redactadas) para mostrarlo al reabrir la app
 * y poder compartirlo. No se envía nada a ningún sitio (sin telemetría).
 */
object RegistroFallos {
    private const val ARCHIVO = "ultimo_fallo.txt"

    fun instalar(context: Context) {
        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, e ->
            runCatching { guardar(context, e, "hilo ${hilo.name}") }
            previo?.uncaughtException(hilo, e)
        }
    }

    fun guardar(context: Context, e: Throwable, donde: String) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val texto = buildString {
            appendLine("Gestor IPTV $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Dónde: $donde")
            appendLine(e.stackTraceToString())
        }
        File(context.filesDir, ARCHIVO).writeText(Redactor.redactar(texto).take(20_000))
    }

    fun leerYBorrar(context: Context): String? {
        val f = File(context.filesDir, ARCHIVO)
        if (!f.exists()) return null
        return f.readText().also { f.delete() }
    }
}
