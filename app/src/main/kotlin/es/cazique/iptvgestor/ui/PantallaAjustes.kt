@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.ConfigMotor
import es.cazique.iptvgestor.core.FuentesEpg
import es.cazique.iptvgestor.core.Normalizacion
import es.cazique.iptvgestor.datos.Ajustes
import es.cazique.iptvgestor.datos.Repositorio
import es.cazique.iptvgestor.datos.TrabajoSincronizacion
import kotlinx.coroutines.launch

private enum class DialogoAjustes { NINGUNO, UA, VARIANTE_EPG, HORAS, CAPAS, PREFIJOS, EXTRAS, BORRAR }

/** Ajustes (sección 6.2.8) y filtros del motor (sección 6.2.5). */
@Composable
fun PantallaAjustes(abrirIntent: (Intent) -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ajustes = app.ajustes
    val repo = app.repositorio
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by ajustes.datos.collectAsState(initial = null)
    val config by ajustes.configMotorFlujo.collectAsState(initial = ConfigMotor())
    var dialogo by remember { mutableStateOf(DialogoAjustes.NINGUNO) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    fun guardarConfig(c: ConfigMotor) = scope.launch { ajustes.guardarConfigMotor(c); repo.recalcular() }
    fun <T> guardar(k: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) = scope.launch { ajustes.guardar(k, v) }

    val exportarDecisiones = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val texto = repo.exportarDecisiones()
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(texto.toByteArray()) }
            mensaje = "Decisiones exportadas (sin credenciales)"
        }
    }
    val importarDecisiones = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            mensaje = try {
                val texto = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                "Importadas ${repo.importarDecisiones(texto)} decisiones"
            } catch (e: Exception) { "Error al importar: ${e.message}" }
        }
    }

    PantallaBase("Ajustes") {
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Tarjeta {
            Text("Cuenta y conexión", style = MaterialTheme.typography.titleMedium)
            FilaFoco(onClick = { nav.ir(Destino.Cuenta) }) { Text("Servidor, usuario y contraseña →") }
            val formato = prefs?.get(Ajustes.K.FORMATO) ?: "ts"
            FilaFoco(onClick = { guardar(Ajustes.K.FORMATO, if (formato == "ts") "m3u8" else "ts") }) {
                Text("Formato de stream", Modifier.weight(1f)); Text(formato)
            }
            FilaFoco(onClick = { dialogo = DialogoAjustes.UA }) {
                Text("User-Agent", Modifier.weight(1f)); Text(prefs?.get(Ajustes.K.USER_AGENT) ?: Repositorio.UA_PREDETERMINADO)
            }
        }
        Tarjeta {
            Text("Filtros y paquetes", style = MaterialTheme.typography.titleMedium)
            FilaFoco(onClick = { dialogo = DialogoAjustes.PREFIJOS }) { Text("Grupos conservados (prefijos)", Modifier.weight(1f)); Text(config.prefijosGrupo.joinToString()) }
            FilaFoco(onClick = { dialogo = DialogoAjustes.EXTRAS }) { Text("Grupos extra", Modifier.weight(1f)); Text(config.extras.joinToString()) }
            FilaInterruptor("PPV de todos los países", config.ppvTodos) { guardarConfig(config.copy(ppvTodos = it)) }
            FilaFoco(onClick = { nav.ir(Destino.Paquetes) }) { Text("Paquetes (${config.paquetes.size}) →") }
            FilaFoco(onClick = { dialogo = DialogoAjustes.CAPAS }) { Text("Máximo de capas", Modifier.weight(1f)); Text("${config.maxCapas}") }
            val ligero = config.preferencia == Normalizacion.PERFIL_LIGERO
            FilaFoco(onClick = { guardarConfig(config.copy(preferencia = if (ligero) Normalizacion.PERFIL_NORMAL else Normalizacion.PERFIL_LIGERO)) }) {
                Text("Perfil de calidad", Modifier.weight(1f)); Text(if (ligero) "Ligero (HD primero)" else "Normal (RAW primero)")
            }
            FilaInterruptor("Variantes BK después, a igual calidad", config.respaldoAlFinal) { guardarConfig(config.copy(respaldoAlFinal = it)) }
            FilaInterruptor("Capas también en el resto de grupos", config.capasEnRestoGrupos) { guardarConfig(config.copy(capasEnRestoGrupos = it)) }
        }
        Tarjeta {
            Text("Guía", style = MaterialTheme.typography.titleMedium)
            FilaFoco(onClick = { dialogo = DialogoAjustes.VARIANTE_EPG }) {
                Text("Variante de dobleM", Modifier.weight(1f)); Text(prefs?.get(Ajustes.K.VARIANTE_EPG) ?: "guiatv_sincolor")
            }
            Text("La guía y los iconos se descargan de dobleM; no van dentro de la app.", style = MaterialTheme.typography.bodySmall)
        }
        Tarjeta {
            Text("Sincronización", style = MaterialTheme.typography.titleMedium)
            val auto = prefs?.get(Ajustes.K.SINCRONIZAR_AUTO) ?: true
            val horas = prefs?.get(Ajustes.K.HORAS_SINCRONIZACION) ?: 6
            FilaInterruptor("Sincronizar automáticamente", auto) {
                scope.launch { ajustes.guardar(Ajustes.K.SINCRONIZAR_AUTO, it); TrabajoSincronizacion.programar(context, horas, it) }
            }
            FilaFoco(onClick = { dialogo = DialogoAjustes.HORAS }) { Text("Cada", Modifier.weight(1f)); Text("$horas horas") }
            FilaFoco(onClick = { nav.ir(Destino.Informes) }) { Text("Informes de cambios →") }
        }
        Tarjeta {
            Text("Lista exportada", style = MaterialTheme.typography.titleMedium)
            FilaInterruptor("Numerar canales (tvg-chno)", prefs?.get(Ajustes.K.NUMERAR) ?: false) { guardar(Ajustes.K.NUMERAR, it) }
            FilaInterruptor("Añadir #EXTVLCOPT con el User-Agent", prefs?.get(Ajustes.K.EXTVLCOPT) ?: false) { guardar(Ajustes.K.EXTVLCOPT, it) }
            FilaInterruptor("Incluir canales para adultos", prefs?.get(Ajustes.K.EXPORTAR_ADULTOS) ?: true) { guardar(Ajustes.K.EXPORTAR_ADULTOS, it) }
            Text("Ninguna de las dos primeras opciones está confirmada en TiviMate: desactivadas por defecto.", style = MaterialTheme.typography.bodySmall)
        }
        Tarjeta {
            Text("Decisiones manuales", style = MaterialTheme.typography.titleMedium)
            FilaFoco(onClick = { nav.ir(Destino.Historial) }) { Text("Historial y deshacer →") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Exportar (JSON)") { exportarDecisiones.launch("decisiones_iptvgestor.json") }
                BotonSecundario("Importar (JSON o alias_epg.txt)") { importarDecisiones.launch(arrayOf("application/json", "text/plain", "*/*")) }
            }
        }
        Tarjeta {
            Text("Aplicación", style = MaterialTheme.typography.titleMedium)
            FilaFoco(onClick = { nav.ir(Destino.Actualizaciones) }) {
                Text("Versión y actualizaciones", Modifier.weight(1f))
                Text("${app.actualizaciones.versionName} (${app.actualizaciones.versionCode})")
            }
            FilaFoco(onClick = { dialogo = DialogoAjustes.BORRAR }) { Text("Borrar todos los datos", color = MaterialTheme.colorScheme.error) }
        }
    }

    when (dialogo) {
        DialogoAjustes.UA -> DialogoTexto("User-Agent", "User-Agent", prefs?.get(Ajustes.K.USER_AGENT) ?: Repositorio.UA_PREDETERMINADO, { dialogo = DialogoAjustes.NINGUNO }) {
            dialogo = DialogoAjustes.NINGUNO; guardar(Ajustes.K.USER_AGENT, it.ifBlank { Repositorio.UA_PREDETERMINADO })
        }
        DialogoAjustes.VARIANTE_EPG -> DialogoOpciones("Variante de la guía", FuentesEpg.VARIANTES.map { it.first to "${it.first}: ${it.second}" }, { dialogo = DialogoAjustes.NINGUNO }) {
            dialogo = DialogoAjustes.NINGUNO
            scope.launch { ajustes.guardar(Ajustes.K.VARIANTE_EPG, it); mensaje = repo.actualizarEpg() ?: "Guía actualizada" }
        }
        DialogoAjustes.HORAS -> DialogoOpciones("Sincronizar cada", listOf(3, 6, 12, 24).map { "$it" to "$it horas" }, { dialogo = DialogoAjustes.NINGUNO }) {
            dialogo = DialogoAjustes.NINGUNO
            scope.launch {
                ajustes.guardar(Ajustes.K.HORAS_SINCRONIZACION, it.toInt())
                TrabajoSincronizacion.programar(context, it.toInt(), ajustes.leer(Ajustes.K.SINCRONIZAR_AUTO) ?: true)
            }
        }
        DialogoAjustes.CAPAS -> DialogoOpciones("Máximo de capas", (1..8).map { "$it" to "$it" }, { dialogo = DialogoAjustes.NINGUNO }) {
            dialogo = DialogoAjustes.NINGUNO; guardarConfig(config.copy(maxCapas = it.toInt()))
        }
        DialogoAjustes.PREFIJOS -> DialogoTexto("Grupos conservados", "Prefijos separados por comas", config.prefijosGrupo.joinToString(", "), { dialogo = DialogoAjustes.NINGUNO }) {
            dialogo = DialogoAjustes.NINGUNO; guardarConfig(config.copy(prefijosGrupo = it.split(',').map(String::trim).filter(String::isNotEmpty)))
        }
        DialogoAjustes.EXTRAS -> DialogoTexto("Grupos extra", "Nombres exactos separados por comas", config.extras.joinToString(", "), { dialogo = DialogoAjustes.NINGUNO }) {
            dialogo = DialogoAjustes.NINGUNO; guardarConfig(config.copy(extras = it.split(',').map(String::trim).filter(String::isNotEmpty)))
        }
        DialogoAjustes.BORRAR -> AlertDialog(
            onDismissRequest = { dialogo = DialogoAjustes.NINGUNO },
            title = { Text("¿Borrar todos los datos?") },
            text = { Text("Se borran la cuenta, los canales, la guía, las decisiones manuales y los ajustes. Exporta antes las decisiones si quieres conservarlas.") },
            confirmButton = { TextButton(onClick = { dialogo = DialogoAjustes.NINGUNO; scope.launch { repo.borrarDatos(); mensaje = "Datos borrados" } }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { dialogo = DialogoAjustes.NINGUNO }) { Text("Cancelar") } },
        )
        DialogoAjustes.NINGUNO -> Unit
    }
}

/** Editor de paquetes: nombre y reglas por texto contenido o prefijo de grupo (sección 4.4). */
@Composable
fun PantallaPaquetes() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val config by app.ajustes.configMotorFlujo.collectAsState(initial = ConfigMotor())
    var nuevo by remember { mutableStateOf(false) }
    fun guardar(c: ConfigMotor) = scope.launch { app.ajustes.guardarConfigMotor(c); app.repositorio.recalcular() }

    PantallaBase("Paquetes", conAtras = true) {
        Text("Cada paquete agrupa los grupos del proveedor de una operadora. Sus canales se reparten en capas (Paquete 1, 2…). Un grupo pertenece al primer paquete cuyas reglas cumple.")
        config.paquetes.forEachIndexed { i, p ->
            Tarjeta {
                Text(p.nombre + if (!p.activo) " (desactivado)" else "", style = MaterialTheme.typography.titleMedium)
                p.reglas.forEach { r -> Text("• ${if (r.tipo.name == "CONTIENE") "contiene" else if (r.tipo.name == "EMPIEZA_POR") "empieza por" else "es"} «${r.texto}»") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonSecundario(if (p.activo) "Desactivar" else "Activar") {
                        guardar(config.copy(paquetes = config.paquetes.toMutableList().also { it[i] = p.copy(activo = !p.activo) }))
                    }
                    if (i > 0) BotonSecundario("Subir") {
                        guardar(config.copy(paquetes = config.paquetes.toMutableList().also { l -> val x = l.removeAt(i); l.add(i - 1, x) }))
                    }
                    BotonSecundario("Eliminar") { guardar(config.copy(paquetes = config.paquetes.filterIndexed { j, _ -> j != i })) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonFoco("Nuevo paquete") { nuevo = true }
            BotonSecundario("Restaurar predeterminados") { guardar(config.copy(paquetes = es.cazique.iptvgestor.core.Paquete.PREDETERMINADOS)) }
        }
    }
    if (nuevo) {
        var nombre by remember { mutableStateOf("") }
        var texto by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { nuevo = false },
            title = { Text("Nuevo paquete") },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre (por ejemplo, DAZN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(texto, { texto = it }, label = { Text("El nombre del grupo contiene… (varios: separa con comas)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    nuevo = false
                    val reglas = texto.split(',').map(String::trim).filter(String::isNotEmpty)
                        .map { es.cazique.iptvgestor.core.Regla(es.cazique.iptvgestor.core.TipoRegla.CONTIENE, it) }
                    if (nombre.isNotBlank() && reglas.isNotEmpty()) guardar(config.copy(paquetes = config.paquetes + es.cazique.iptvgestor.core.Paquete(nombre.trim(), reglas)))
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { nuevo = false }) { Text("Cancelar") } },
        )
    }
}
