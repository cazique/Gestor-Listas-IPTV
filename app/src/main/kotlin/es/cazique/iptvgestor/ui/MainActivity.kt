package es.cazique.iptvgestor.ui

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import es.cazique.iptvgestor.IptvGestorApp
import es.cazique.iptvgestor.RegistroFallos
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pedirNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IptvGestorApp
        val tv = esTelevision(this)
        lifecycleScope.launch {
            app.actualizaciones.confirmaciones.collect { runCatching { startActivity(it) } }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        val buscarAhora = intent?.getBooleanExtra(EXTRA_BUSCAR, false) == true
        val falloAnterior = RegistroFallos.leerYBorrar(this)
        setContent {
            TemaApp(tv) {
                AppRaiz(app, tv, buscarAhora, abrirIntent = { runCatching { startActivity(it) } })
                falloAnterior?.let { DialogoFallo(it) { t -> runCatching { startActivity(t) } } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_BUSCAR, false)) {
            val app = application as IptvGestorApp
            lifecycleScope.launch { app.actualizaciones.comprobar() }
        }
    }

    override fun onResume() { super.onResume(); visible = true }
    override fun onPause() { visible = false; super.onPause() }

    companion object {
        const val EXTRA_BUSCAR = "buscar_actualizaciones"
        @Volatile var visible = false

        /** Televisión: modo de interfaz TV o característica leanback. */
        fun esTelevision(context: Context): Boolean {
            val ui = context.getSystemService(UiModeManager::class.java)
            return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }
    }
}
