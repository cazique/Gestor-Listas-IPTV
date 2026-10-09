package es.cazique.iptvgestor.actualizacion

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import es.cazique.iptvgestor.BuildConfig
import es.cazique.iptvgestor.core.ComprobadorActualizaciones
import es.cazique.iptvgestor.core.DescargadorApk
import es.cazique.iptvgestor.core.ErrorActualizacion
import es.cazique.iptvgestor.core.InfoActualizacion
import es.cazique.iptvgestor.core.ResultadoComprobacion
import es.cazique.iptvgestor.core.VerificacionFirma
import es.cazique.iptvgestor.datos.Ajustes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

sealed interface EstadoActualizacion {
    data object Inactivo : EstadoActualizacion
    data object Buscando : EstadoActualizacion
    data object AlDia : EstadoActualizacion
    data class Disponible(val info: InfoActualizacion, val preliminar: Boolean) : EstadoActualizacion
    data class Descargando(val porcentaje: Int) : EstadoActualizacion
    data object Verificando : EstadoActualizacion
    data class NecesitaPermiso(val apk: File) : EstadoActualizacion
    data object Instalando : EstadoActualizacion
    data class Error(val mensaje: String) : EstadoActualizacion
}

/**
 * Autoactualización desde los Releases de GitHub (sección 6.4.4):
 * comprobar → descargar a la caché → SHA-256 → misma firma → PackageInstaller (Android pide confirmación).
 */
class GestorActualizaciones(
    private val context: Context,
    private val http: OkHttpClient,
    private val ajustes: Ajustes,
) {
    private val _estado = MutableStateFlow<EstadoActualizacion>(EstadoActualizacion.Inactivo)
    val estado: StateFlow<EstadoActualizacion> = _estado.asStateFlow()

    /** Intents de confirmación de Android que la actividad visible debe abrir. */
    val confirmaciones = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    val versionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    val versionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    private val dirDescargas get() = File(context.cacheDir, "actualizaciones")

    fun comprobador() = ComprobadorActualizaciones(http, BuildConfig.REPO_ACTUALIZACIONES, ajustes.cacheHttp())

    suspend fun comprobar(): ResultadoComprobacion = withContext(Dispatchers.IO) {
        _estado.value = EstadoActualizacion.Buscando
        val r = comprobador().comprobar(versionCode, Build.VERSION.SDK_INT, ajustes.canalPruebas.first())
        ajustes.guardar(Ajustes.K.ULTIMA_COMPROBACION, System.currentTimeMillis())
        _estado.value = when (r) {
            is ResultadoComprobacion.Disponible -> EstadoActualizacion.Disponible(r.info, r.preliminar)
            is ResultadoComprobacion.AlDia -> EstadoActualizacion.AlDia
            is ResultadoComprobacion.Incompatible -> EstadoActualizacion.Error("La versión ${r.info.versionName} necesita Android más reciente")
            is ResultadoComprobacion.Error -> EstadoActualizacion.Error(r.mensaje)
        }
        r
    }

    /** Al abrir la app: como mucho una vez cada 12 horas. */
    suspend fun comprobarSiToca() {
        if (!ajustes.comprobarAuto.first()) return
        val ultima = ajustes.leer(Ajustes.K.ULTIMA_COMPROBACION) ?: 0L
        if (System.currentTimeMillis() - ultima >= 12 * 60 * 60 * 1000L) comprobar()
    }

    suspend fun descargarEInstalar(info: InfoActualizacion) = withContext(Dispatchers.IO) {
        try {
            if (info.versionCode <= versionCode) { _estado.value = EstadoActualizacion.AlDia; return@withContext }
            dirDescargas.deleteRecursively()
            val destino = File(dirDescargas, "app-release.apk")
            _estado.value = EstadoActualizacion.Descargando(0)
            DescargadorApk(http).descargar(info, destino) { leido, total ->
                if (total > 0) _estado.value = EstadoActualizacion.Descargando((leido * 100 / total).toInt())
            }
            _estado.value = EstadoActualizacion.Verificando
            val (paquete, firmasApk) = Firma.deApk(context, destino.absolutePath)
            if (paquete != context.packageName) throw ErrorActualizacion("El APK no es de esta app")
            if (!VerificacionFirma.misma(firmasApk, Firma.instalada(context))) {
                destino.delete()
                throw ErrorActualizacion("El APK no está firmado con la misma clave que la app instalada")
            }
            instalarOPedirPermiso(destino)
        } catch (e: Exception) {
            _estado.value = EstadoActualizacion.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    fun puedeInstalar(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun instalarOPedirPermiso(apk: File) {
        if (!puedeInstalar()) { _estado.value = EstadoActualizacion.NecesitaPermiso(apk); return }
        instalar(apk)
    }

    /** Pantalla de "Instalar apps desconocidas" para esta app; en algunas TV no existe y se abre la de seguridad. */
    fun intentPermiso(): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun abrirAjustesPermiso(abrir: (Intent) -> Unit) {
        for (i in intentPermiso()) {
            try { abrir(i); return } catch (_: ActivityNotFoundException) { }
        }
    }

    private fun instalar(apk: File) {
        _estado.value = EstadoActualizacion.Instalando
        val instalador = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = instalador.createSession(params)
        instalador.openSession(id).use { sesion ->
            sesion.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                sesion.fsync(out)
            }
            val intent = Intent(context, ReceptorInstalacion::class.java).setAction(ReceptorInstalacion.ACCION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pendiente = PendingIntent.getBroadcast(context, id, intent, flags)
            sesion.commit(pendiente.intentSender)
        }
    }

    internal fun resultadoInstalacion(estado: Int, mensaje: String?) {
        _estado.value = when (estado) {
            PackageInstaller.STATUS_SUCCESS -> EstadoActualizacion.Inactivo
            PackageInstaller.STATUS_PENDING_USER_ACTION -> EstadoActualizacion.Instalando
            PackageInstaller.STATUS_FAILURE_ABORTED -> EstadoActualizacion.Error("Instalación cancelada")
            else -> EstadoActualizacion.Error("La instalación falló (${mensaje ?: estado})")
        }
    }

    /** Tras actualizar, la primera apertura muestra "Actualizada a la versión X". */
    suspend fun versionRecienActualizada(): String? {
        val vista = ajustes.leer(Ajustes.K.ULTIMA_VERSION_VISTA) ?: 0L
        val actual = versionCode
        if (vista == actual) return null
        ajustes.guardar(Ajustes.K.ULTIMA_VERSION_VISTA, actual)
        dirDescargas.deleteRecursively()
        return if (vista in 1 until actual) versionName else null
    }

    fun limpiarEstado() { _estado.value = EstadoActualizacion.Inactivo }
}
