package es.cazique.iptvgestor.datos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import es.cazique.iptvgestor.IptvGestorApp
import java.util.concurrent.TimeUnit

/** Sincronización periódica (cada 6 horas por defecto) y actualización de la guía (sección 10). */
class TrabajoSincronizacion(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IptvGestorApp
        if (app.repositorio.credenciales.leer() == null) return Result.success()
        val r = app.repositorio.sincronizar()
        if (r.error != null) return if (runAttemptCount < 3) Result.retry() else Result.failure()
        app.exportador.exportarAuto()
        if (app.ajustes.leer(Ajustes.K.SUBIR_AUTO) == true) app.repositorio.subirLista(app.exportador)
        return Result.success()
    }

    companion object {
        private const val NOMBRE = "sincronizacion"

        fun programar(context: Context, horas: Int, activa: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!activa) { wm.cancelUniqueWork(NOMBRE); return }
            val peticion = PeriodicWorkRequestBuilder<TrabajoSincronizacion>(horas.coerceIn(1, 48).toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.UPDATE, peticion)
        }
    }
}
