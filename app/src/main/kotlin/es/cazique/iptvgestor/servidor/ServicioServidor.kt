package es.cazique.iptvgestor.servidor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import es.cazique.iptvgestor.IptvGestorApp
import es.cazique.iptvgestor.R
import es.cazique.iptvgestor.core.ModoExportacion
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

data class EstadoServidor(val activo: Boolean, val direcciones: List<String> = emptyList(), val error: String? = null)

/**
 * Servidor HTTP opcional (sección 7.2). Desactivado por defecto; solo lo arranca el usuario.
 * Por defecto escucha solo en 127.0.0.1; la red local exige una ruta con token aleatorio.
 * Rutas: /lista.m3u, /lista_<paquete>.m3u, /informe.txt
 */
class ServicioServidor : Service() {
    private var servidor: EmbeddedServer<*, *>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_PARAR) { parar(); return START_NOT_STICKY }
        val red = intent?.getBooleanExtra(EXTRA_RED_LOCAL, false) == true
        val token = intent?.getStringExtra(EXTRA_TOKEN).orEmpty()
        val puerto = intent?.getIntExtra(EXTRA_PUERTO, PUERTO) ?: PUERTO
        val tipo = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 2, notificacion(red), tipo)
        servidor?.stop(500, 1000)
        try {
            servidor = arrancar(if (red) "0.0.0.0" else "127.0.0.1", puerto, if (red) token else "")
            val base = if (red) ipsLocales().map { "http://$it:$puerto/$token" } else listOf("http://127.0.0.1:$puerto")
            _estado.value = EstadoServidor(true, base.map { "$it/lista.m3u" })
        } catch (e: Exception) {
            _estado.value = EstadoServidor(false, error = e.message)
            parar()
        }
        return START_NOT_STICKY
    }

    private fun arrancar(host: String, puerto: Int, token: String): EmbeddedServer<*, *> {
        val app = application as IptvGestorApp
        val prefijo = if (token.isEmpty()) "" else "/$token"
        return embeddedServer(CIO, port = puerto, host = host) {
            routing {
                get("$prefijo/lista.m3u") {
                    val texto = runCatching { app.exportador.generar(ModoExportacion.TODO).first().contenido }.getOrNull()
                    if (texto == null) call.respondText("Sin datos", status = HttpStatusCode.ServiceUnavailable)
                    else call.respondText(texto, ContentType.parse("audio/x-mpegurl; charset=utf-8"))
                }
                get("$prefijo/{archivo}") {
                    val pedido = call.parameters["archivo"].orEmpty()
                    if (pedido == "informe.txt") {
                        call.respondText(app.repositorio.ultimoInforme()?.comoTexto() ?: "Sin informes", ContentType.Text.Plain)
                        return@get
                    }
                    val archivos = runCatching { app.exportador.generar(ModoExportacion.POR_PAQUETE) }.getOrDefault(emptyList())
                    val a = archivos.firstOrNull { it.nombre == pedido }
                    if (a == null) call.respondText("No encontrado", status = HttpStatusCode.NotFound)
                    else call.respondText(a.contenido, ContentType.parse("audio/x-mpegurl; charset=utf-8"))
                }
            }
        }.start(wait = false)
    }

    private fun notificacion(red: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Servidor local", NotificationManager.IMPORTANCE_LOW))
        val parar = PendingIntent.getService(this, 0, Intent(this, ServicioServidor::class.java).setAction(ACCION_PARAR), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_frente)
            .setContentTitle("Servidor de la lista activo")
            .setContentText(if (red) "Accesible en la red local (con ruta secreta)" else "Solo en este dispositivo (127.0.0.1)")
            .setOngoing(true)
            .addAction(0, "Parar", parar)
            .build()
    }

    private fun parar() {
        servidor?.stop(500, 1000)
        servidor = null
        _estado.value = EstadoServidor(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        servidor?.stop(500, 1000)
        servidor = null
        _estado.value = EstadoServidor(false)
        super.onDestroy()
    }

    companion object {
        const val PUERTO = 8484
        private const val CANAL = "servidor"
        private const val ACCION_PARAR = "parar"
        private const val EXTRA_RED_LOCAL = "red_local"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PUERTO = "puerto"
        private val _estado = MutableStateFlow(EstadoServidor(false))
        val estado: StateFlow<EstadoServidor> = _estado.asStateFlow()

        fun nuevoToken(): String {
            val b = ByteArray(18).also { SecureRandom().nextBytes(it) }
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)
        }

        fun iniciar(context: Context, redLocal: Boolean, token: String, puerto: Int = PUERTO) {
            val i = Intent(context, ServicioServidor::class.java)
                .putExtra(EXTRA_RED_LOCAL, redLocal).putExtra(EXTRA_TOKEN, token).putExtra(EXTRA_PUERTO, puerto)
            ContextCompat.startForegroundService(context, i)
        }

        fun detener(context: Context) {
            context.startService(Intent(context, ServicioServidor::class.java).setAction(ACCION_PARAR))
        }

        fun ipsLocales(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

    }
}
