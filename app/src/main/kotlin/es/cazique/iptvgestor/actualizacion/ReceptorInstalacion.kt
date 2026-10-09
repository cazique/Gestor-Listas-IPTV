package es.cazique.iptvgestor.actualizacion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import es.cazique.iptvgestor.IptvGestorApp
import es.cazique.iptvgestor.ui.MainActivity

/** Recibe el resultado de la sesión de PackageInstaller. */
class ReceptorInstalacion : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACCION) return
        val app = context.applicationContext as IptvGestorApp
        val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        app.actualizaciones.resultadoInstalacion(estado, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        if (estado == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmar = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
            // La actividad visible abre la confirmación; si no hay ninguna, se abre desde aquí.
            if (!MainActivity.visible || !app.actualizaciones.confirmaciones.tryEmit(confirmar)) {
                confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmar) }
            }
        }
    }

    companion object {
        const val ACCION = "es.cazique.iptvgestor.RESULTADO_INSTALACION"
    }
}
