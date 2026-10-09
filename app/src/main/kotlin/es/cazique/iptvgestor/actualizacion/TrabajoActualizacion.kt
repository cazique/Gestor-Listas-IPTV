package es.cazique.iptvgestor.actualizacion

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import es.cazique.iptvgestor.IptvGestorApp
import es.cazique.iptvgestor.R
import es.cazique.iptvgestor.core.ResultadoComprobacion
import es.cazique.iptvgestor.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Comprobación diaria con WorkManager; si hay versión nueva, avisa con una notificación. */
class TrabajoActualizacion(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as IptvGestorApp
        if (!app.ajustes.comprobarAuto.first()) return Result.success()
        return when (val r = app.actualizaciones.comprobar()) {
            is ResultadoComprobacion.Disponible -> { notificar(r.info.versionName); Result.success() }
            is ResultadoComprobacion.Error -> Result.retry()
            else -> Result.success()
        }
    }

    private fun notificar(version: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_BUSCAR, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, IptvGestorApp.CANAL_ACTUALIZACIONES)
            .setSmallIcon(R.drawable.ic_launcher_frente)
            .setContentTitle(ctx.getString(R.string.notif_titulo))
            .setContentText(ctx.getString(R.string.notif_texto, version))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
    }

    companion object {
        fun programar(context: Context) {
            val peticion = PeriodicWorkRequestBuilder<TrabajoActualizacion>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("actualizacion", ExistingPeriodicWorkPolicy.KEEP, peticion)
        }
    }
}
