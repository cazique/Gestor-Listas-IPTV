package es.cazique.iptvgestor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import androidx.work.WorkManager
import es.cazique.iptvgestor.actualizacion.GestorActualizaciones
import es.cazique.iptvgestor.actualizacion.TrabajoActualizacion
import es.cazique.iptvgestor.datos.Ajustes
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Contenedor de dependencias de la app (inyección manual, ver DECISIONES.md). */
class IptvGestorApp : Application() {

    lateinit var ajustes: Ajustes
        private set
    lateinit var http: OkHttpClient
        private set
    lateinit var actualizaciones: GestorActualizaciones
        private set

    override fun onCreate() {
        super.onCreate()
        ajustes = Ajustes(this)
        // Sin interceptor de registro: nunca se escriben URL ni cuerpos en el log (sección 9).
        http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        actualizaciones = GestorActualizaciones(this, http, ajustes)
        WorkManager.initialize(this, Configuration.Builder().build())
        crearCanalNotificaciones()
        TrabajoActualizacion.programar(this)
    }

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
