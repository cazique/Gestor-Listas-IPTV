package es.cazique.iptvgestor.ui

import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import es.cazique.iptvgestor.core.Decisiones
import kotlinx.coroutines.launch

/** Historial de decisiones manuales con deshacer y rehacer (sección 5.1.9). */
@Composable
fun PantallaHistorial() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val historial by app.repositorio.historial.collectAsState(initial = emptyList())
    val vigentes = Decisiones.vigentes(historial).map { it.id }.toSet()
    PantallaBase("Historial (${historial.size})", conAtras = true) {
        if (historial.isEmpty()) Text("Todavía no hay decisiones manuales.")
        historial.forEach { d ->
            Tarjeta {
                Text("${d.tipo.descripcion}: ${d.nombre.ifEmpty { d.clave }}", style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(
                    d.grupo.takeIf { it.isNotEmpty() }?.let { "grupo $it" },
                    d.valor.takeIf { it.isNotEmpty() }?.let { "→ $it" },
                    fechaCorta(d.fecha),
                    if (!d.activa) "deshecha" else if (d.id !in vigentes) "sustituida por otra posterior" else null,
                ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (d.activa) BotonSecundario("Deshacer") { scope.launch { app.repositorio.deshacer(d.id) } }
                else BotonSecundario("Rehacer") { scope.launch { app.repositorio.rehacer(d.id) } }
            }
        }
    }
}

/** Informes de cambios de las sincronizaciones, compartibles como texto (sección 6.2.6). */
@Composable
fun PantallaInformes(abrirIntent: (Intent) -> Unit) {
    val app = LocalApp.current
    val informes by app.repositorio.informes.collectAsState(initial = emptyList())
    PantallaBase("Informes de cambios", conAtras = true) {
        if (informes.isEmpty()) Text("Aún no hay sincronizaciones.")
        informes.forEach { inf ->
            Tarjeta {
                Text(fechaCorta(inf.fecha), style = MaterialTheme.typography.titleMedium)
                val texto = inf.comoTexto(maximo = 40)
                Text(texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier)
                BotonSecundario("Compartir") {
                    abrirIntent(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inf.comoTexto()), "Compartir informe"))
                }
            }
        }
    }
}
