@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.ModoExportacion
import es.cazique.iptvgestor.datos.Ajustes
import kotlinx.coroutines.launch

private enum class Accion { CARPETA, COMPARTIR }

/** Exportación (sección 7.1) e instrucciones para TiviMate (sección 7.4). */
@Composable
fun PantallaExportar(abrirIntent: (Intent) -> Unit) {
    val app = LocalApp.current
    val ajustes = app.ajustes
    val context = LocalContext.current
    val portapapeles = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs by ajustes.datos.collectAsState(initial = null)
    var urlGuia by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var pendiente by remember { mutableStateOf<Accion?>(null) }
    var noMostrar by remember { mutableStateOf(false) }
    val modo = runCatching { ModoExportacion.valueOf(prefs?.get(Ajustes.K.MODO_EXPORTACION) ?: "TODO") }.getOrDefault(ModoExportacion.TODO)
    val carpeta = prefs?.get(Ajustes.K.CARPETA_EXPORTACION)

    LaunchedEffect(prefs) { urlGuia = app.repositorio.urlGuia() }

    val elegirCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            ajustes.guardar(Ajustes.K.CARPETA_EXPORTACION, uri.toString())
            mensaje = "Carpeta elegida"
        }
    }

    fun ejecutar(a: Accion) = scope.launch {
        mensaje = try {
            val archivos = app.exportador.generar()
            val informe = app.repositorio.ultimoInforme()?.comoTexto()
            when (a) {
                Accion.CARPETA -> {
                    val c = carpeta ?: return@launch run { mensaje = "Elige antes una carpeta" }
                    app.exportador.guardarEnCarpeta(Uri.parse(c), archivos, informe?.let { mapOf("informe.txt" to it) } ?: emptyMap())
                    "Guardados ${archivos.size} archivo(s): " + archivos.joinToString { "${it.nombre} (${it.entradas})" }
                }
                Accion.COMPARTIR -> {
                    abrirIntent(app.exportador.intentCompartir(archivos.map { it.nombre to it.contenido }))
                    null
                }
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun pedir(a: Accion) {
        if (prefs?.get(Ajustes.K.AVISO_EXPORTAR_VISTO) == true) ejecutar(a) else pendiente = a
    }

    PantallaBase("Exportar") {
        Tarjeta {
            Text("Formato de salida", style = MaterialTheme.typography.titleMedium)
            listOf(
                ModoExportacion.TODO to "Todo en un archivo (lista.m3u)",
                ModoExportacion.POR_PAQUETE to "Un archivo por paquete o grupo",
                ModoExportacion.POR_CAPA to "Un archivo por capa o grupo (sin tvg-id repetidos: recomendado si TiviMate no muestra la guía en las capas repetidas)",
            ).forEach { (m, t) ->
                FilaFoco(onClick = { scope.launch { ajustes.guardar(Ajustes.K.MODO_EXPORTACION, m.name) } }) {
                    RadioButton(selected = modo == m, onClick = null)
                    Text(t)
                }
            }
        }
        Tarjeta {
            Text("Guardar o compartir", style = MaterialTheme.typography.titleMedium)
            Text("Carpeta: " + (carpeta?.let { Uri.parse(it).lastPathSegment } ?: "sin elegir"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Elegir carpeta") {
                    try { elegirCarpeta.launch(null) } catch (e: Exception) { mensaje = "Este dispositivo no tiene selector de carpetas: usa Compartir" }
                }
                BotonFoco("Guardar en la carpeta", habilitado = carpeta != null) { pedir(Accion.CARPETA) }
                BotonSecundario("Compartir") { pedir(Accion.COMPARTIR) }
            }
            FilaInterruptor("Exportar automáticamente tras cada sincronización", prefs?.get(Ajustes.K.EXPORTAR_AUTO) ?: false) { v ->
                scope.launch { ajustes.guardar(Ajustes.K.EXPORTAR_AUTO, v) }
            }
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        }
        Tarjeta {
            Text("Guía para TiviMate", style = MaterialTheme.typography.titleMedium)
            Text(urlGuia, style = MaterialTheme.typography.bodySmall)
            BotonSecundario("Copiar dirección de la guía") { portapapeles.setText(AnnotatedString(urlGuia)); mensaje = "Dirección copiada" }
            Text(
                "TiviMate puede no leer la guía de la cabecera de la lista. En TiviMate:\n" +
                    "1. Añade la lista como «Lista M3U» (por archivo o URL), no como Xtream Codes: Xtream no pasaría por esta limpieza.\n" +
                    "2. En Ajustes → Guía → Fuentes de guía, añade una fuente XMLTV con la dirección de arriba (cada 12–24 horas).\n" +
                    "3. Activa esa fuente dentro de los ajustes de la lista (en Premium hay que activarla por lista).\n" +
                    "4. Lo que no empareje por tvg-id solo se puede asignar en TiviMate a mano, canal a canal: por eso conviene resolverlo aquí, en «Revisar».\n" +
                    "5. Si los canales no abren, fija el User-Agent (VLC/3.0.20) en la configuración de la lista dentro de TiviMate.\n" +
                    "6. La cuenta admite una sola conexión: la vista múltiple o grabar mientras ves otro canal pueden necesitar más.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    pendiente?.let { a ->
        AlertDialog(
            onDismissRequest = { pendiente = null },
            title = { Text("La lista contiene tu usuario y contraseña") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Cada dirección de la lista lleva las credenciales del proveedor. No la compartas ni la subas a sitios públicos.")
                    FilaFoco(onClick = { noMostrar = !noMostrar }) {
                        Checkbox(checked = noMostrar, onCheckedChange = null)
                        Text("No volver a mostrar")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendiente = null
                    scope.launch { if (noMostrar) ajustes.guardar(Ajustes.K.AVISO_EXPORTAR_VISTO, true) }
                    ejecutar(a)
                }) { Text("Entendido, exportar") }
            },
            dismissButton = { TextButton(onClick = { pendiente = null }) { Text("Cancelar") } },
        )
    }
}
