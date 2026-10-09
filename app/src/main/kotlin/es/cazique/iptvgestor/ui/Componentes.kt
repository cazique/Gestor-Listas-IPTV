package es.cazique.iptvgestor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import es.cazique.iptvgestor.R
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

/** Columna desplazable con cabecera (título, subtítulo opcional y botón Atrás si hay pila). */
@Composable
fun PantallaBase(
    titulo: String,
    conAtras: Boolean = false,
    subtitulo: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalNav.current
    val tv = LocalTv.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (tv) 40.dp else 16.dp, vertical = if (tv) 24.dp else 12.dp)
            .widthIn(max = 1100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cabecera(titulo, conAtras, subtitulo) { nav.atras() }
        contenido()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Cabecera(titulo: String, conAtras: Boolean, subtitulo: String? = null, atras: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conAtras) {
            IconButton(onClick = atras, modifier = Modifier.semantics { contentDescription = "Atrás" }) {
                Icon(painterResource(R.drawable.ic_atras), contentDescription = null)
            }
        }
        Column {
            Text(titulo, style = MaterialTheme.typography.headlineSmall)
            subtitulo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Tarjeta con título e icono opcionales. */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    icono: Int? = null,
    destacada: Boolean = false,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (destacada) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (titulo != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    icono?.let { Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                    Text(titulo, style = MaterialTheme.typography.titleMedium)
                }
            }
            contenido()
        }
    }
}

/** Cifra destacada (número grande con etiqueta). */
@Composable
fun Cifra(valor: String, etiqueta: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(valor, style = MaterialTheme.typography.displaySmall, color = color, maxLines = 1)
            Text(etiqueta, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

/** Etiqueta pequeña de color (capa, estado, aviso). */
@Composable
fun Etiqueta(texto: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Text(texto, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/** Estado vacío con icono y explicación. */
@Composable
fun EstadoVacio(icono: Int, titulo: String, texto: String, accion: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(icono), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(18.dp).size(36.dp))
        }
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        accion?.invoke()
    }
}

/** Fila pulsable con marco visible cuando tiene el foco del mando. */
@Composable
fun FilaFoco(onClick: () -> Unit, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    var foco by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .onFocusChanged { foco = it.isFocused }
            .clip(forma)
            .background(if (foco) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(if (foco) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, forma) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { contenido() }
}

@Composable
fun IconoCanal(url: String, tamano: Int = 40) {
    Box(
        Modifier.size(tamano.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) Icon(painterResource(R.drawable.ic_tv), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size((tamano * 0.55f).dp))
        else AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
    }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
