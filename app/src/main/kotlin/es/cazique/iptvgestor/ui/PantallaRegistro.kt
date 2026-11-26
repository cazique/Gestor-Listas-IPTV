@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.R
import es.cazique.iptvgestor.RegistroActividad

/** Registro de actividad: cada conexión, respuesta y error, sin credenciales, para diagnosticar y compartir. */
@Composable
fun PantallaRegistro(abrirIntent: (Intent) -> Unit) {
    val lineas by RegistroActividad.flujo.collectAsState()
    val nav = LocalNav.current
    val tv = LocalTv.current
    Column(Modifier.fillMaxSize().padding(horizontal = if (tv) 40.dp else 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Cabecera("Registro de actividad", nav.pila.size > 1, "${lineas.size} líneas · sin usuario ni contraseña") { nav.atras() }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonFoco("Compartir registro") {
                abrirIntent(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, RegistroActividad.texto()),
                    "Compartir registro",
                ))
            }
            BotonSecundario("Borrar") { RegistroActividad.borrar() }
        }
        if (lineas.isEmpty()) {
            EstadoVacio(R.drawable.ic_lista, "Registro vacío", "Aquí aparecerá cada conexión con el servidor, la guía y las actualizaciones.")
        } else {
            // Lo más reciente arriba.
            val recientes = lineas.asReversed()
            LazyColumn(Modifier.fillMaxSize().testTag("registro")) {
                itemsIndexed(recientes) { _, l ->
                    val color = when {
                        " ✗ " in l -> MaterialTheme.colorScheme.error
                        " ! " in l -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(l, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}
