package es.cazique.iptvgestor.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.cazique.iptvgestor.core.CanalEpg
import es.cazique.iptvgestor.core.Emparejador

/** Columna desplazable con cabecera y botón Atrás (si hay pila). */
@Composable
fun PantallaBase(titulo: String, conAtras: Boolean = false, contenido: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNav.current
    val tv = LocalTv.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (tv) 32.dp else 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cabecera(titulo, conAtras) { nav.atras() }
        contenido()
        Spacer(Modifier.padding(16.dp))
    }
}

@Composable
fun Cabecera(titulo: String, conAtras: Boolean, atras: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conAtras) TextButton(onClick = atras) { Text("← Atrás") }
        Text(titulo, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido) }
}

/** Fila pulsable con marco visible cuando tiene el foco del mando. */
@Composable
fun FilaFoco(onClick: () -> Unit, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    var foco by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onFocusChanged { foco = it.isFocused }
            .then(if (foco) Modifier.border(3.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { contenido() }
}

@Composable
fun IconoCanal(url: String, tamano: Int = 40) {
    if (url.isBlank()) Spacer(Modifier.size(tamano.dp))
    else AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(tamano.dp))
}

@Composable
fun BotonSecundario(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var foco by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.onFocusChanged { foco = it.isFocused }
            .then(if (foco) Modifier.border(3.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(24.dp)) else Modifier),
    ) { Text(texto) }
}

@Composable
fun Dato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Buscador sobre los canales de la guía, con nombre e icono (sección 5.1.3). */
@Composable
fun SelectorEpg(emparejador: Emparejador?, inicial: String, cerrar: () -> Unit, elegir: (CanalEpg) -> Unit) {
    var q by remember { mutableStateOf(inicial) }
    val resultados = remember(q, emparejador) { emparejador?.buscarTexto(q, 60).orEmpty() }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Asignar guía") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text("Buscar en la guía") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (emparejador == null || emparejador.epg.isEmpty()) Text("No hay guía descargada todavía.")
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(resultados, key = { it.id }) { c ->
                        FilaFoco(onClick = { elegir(c) }) {
                            IconoCanal(c.icono, 32)
                            Column(Modifier.weight(1f)) {
                                Text(c.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.nombres.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            Text("#${c.posicion + 1}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

/** Diálogo para escribir un texto (URL de icono, nombre de paquete…). */
@Composable
fun DialogoTexto(titulo: String, etiqueta: String, inicial: String = "", cerrar: () -> Unit, aceptar: (String) -> Unit) {
    var t by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(titulo) },
        text = { OutlinedTextField(t, { t = it }, label = { Text(etiqueta) }, singleLine = true, modifier = Modifier.width(420.dp)) },
        confirmButton = { TextButton(onClick = { aceptar(t.trim()) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

/** Diálogo de opciones de una sola elección. */
@Composable
fun DialogoOpciones(titulo: String, opciones: List<Pair<String, String>>, cerrar: () -> Unit, elegir: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(titulo) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(opciones) { (valor, texto) -> FilaFoco(onClick = { elegir(valor) }) { Text(texto) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

fun fechaCorta(ms: Long?): String =
    if (ms == null || ms <= 0) "nunca"
    else java.text.SimpleDateFormat("d/M/yyyy HH:mm", java.util.Locale.forLanguageTag("es-ES")).format(java.util.Date(ms))
