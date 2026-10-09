package es.cazique.iptvgestor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import es.cazique.iptvgestor.actualizacion.GestorActualizaciones
import es.cazique.iptvgestor.actualizacion.TrabajoActualizacion
import es.cazique.iptvgestor.datos.Ajustes
import es.cazique.iptvgestor.datos.Credenciales
import es.cazique.iptvgestor.datos.Exportador
import es.cazique.iptvgestor.datos.Repositorio
import es.cazique.iptvgestor.datos.TrabajoSincronizacion
import es.cazique.iptvgestor.datos.bd.BaseDatos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Contenedor de dependencias de la app (inyección manual, ver DECISIONES.md). */
class IptvGestorApp : Application(), Configuration.Provider {

    lateinit var ajustes: Ajustes
        private set
    lateinit var http: OkHttpClient
        private set
    lateinit var actualizaciones: GestorActualizaciones
        private set
    lateinit var repositorio: Repositorio
        private set
    lateinit var exportador: Exportador
        private set
    lateinit var vod: es.cazique.iptvgestor.datos.RepositorioVod
        private set
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ajustes = Ajustes(this)
        // Sin interceptor de registro: nunca se escriben URL ni cuerpos en el log (sección 9).
        http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        actualizaciones = GestorActualizaciones(this, http, ajustes)
        repositorio = Repositorio(this, http, ajustes, Credenciales(this), BaseDatos.crear(this))
        exportador = Exportador(this, repositorio)
        vod = es.cazique.iptvgestor.datos.RepositorioVod(this, repositorio, http)
        crearCanalNotificaciones()
        TrabajoActualizacion.programar(this)
        alcance.launch {
            TrabajoSincronizacion.programar(
                this@IptvGestorApp,
                ajustes.leer(Ajustes.K.HORAS_SINCRONIZACION) ?: 6,
                ajustes.leer(Ajustes.K.SINCRONIZAR_AUTO) ?: true,
            )
            repositorio.recalcular()
        }
    }

    /** WorkManager se inicializa bajo demanda con esta configuración (el inicializador automático está quitado). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    private fun crearCanalNotificaciones() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ACTUALIZACIONES, getString(R.string.notif_canal), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CANAL_ACTUALIZACIONES = "actualizaciones"
    }
}
