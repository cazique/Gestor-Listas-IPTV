@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.EstadoEnlace
import es.cazique.iptvgestor.core.Pin
import es.cazique.iptvgestor.core.TipoAmbito
import es.cazique.iptvgestor.datos.Ajustes
import es.cazique.iptvgestor.servidor.ServicioServidor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Desbloqueo del contenido para adultos durante la sesión (sección 6.2.9). */
object ControlParental {
    val desbloqueado = MutableStateFlow(false)
}

private enum class DialogoUtil { NINGUNO, RED, SUBIDA, PIN_NUEVO, PIN_QUITAR, ESPERA }

/** Servidor local, subida a servidor propio, comprobación de enlaces y bloqueo parental (Fase 3). */
@Composable
fun PantallaUtilidades() {
    val app = LocalApp.current
    val repo = app.repositorio
    val ajustes = app.ajustes
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by ajustes.datos.collectAsState(initial = null)
    val servidor by ServicioServidor.estado.collectAsState()
    val resultado by repo.resultado.collectAsState()
    val progreso by repo.progreso.collectAsState()
    val enlaces by repo.resultadosEnlaces.collectAsState(initial = emptyMap())
    val config by ajustes.configMotorFlujo.collectAsState(initial = es.cazique.iptvgestor.core.ConfigMotor())
    var dialogo by remember { mutableStateOf(DialogoUtil.NINGUNO) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var cancelar by remember { mutableStateOf(false) }
    var comprobando by remember { mutableStateOf(false) }
    var subida by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    LaunchedEffect(Unit) { subida = repo.credenciales.leerSubida() }

    val red = prefs?.get(Ajustes.K.SERVIDOR_RED) ?: false
    fun arrancar(enRed: Boolean) = scope.launch {
        val token = ajustes.leer(Ajustes.K.SERVIDOR_TOKEN) ?: ServicioServidor.nuevoToken().also { ajustes.guardar(Ajustes.K.SERVIDOR_TOKEN, it) }
        ajustes.guardar(Ajustes.K.SERVIDOR_RED, enRed)
        ServicioServidor.iniciar(context, enRed, token)
    }

    PantallaBase("Utilidades", conAtras = true) {
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Tarjeta(titulo = "Servidor local de la lista") {
            Text("Para que TiviMate lea una dirección en lugar de un archivo. Solo funciona mientras está encendido (con notificación y botón de parada).")
            FilaInterruptor("Servidor encendido", servidor.activo) { on ->
                if (on) arrancar(red) else ServicioServidor.detener(context)
            }
            FilaInterruptor("Accesible desde la red local (ruta secreta)", red) { on ->
                if (on) dialogo = DialogoUtil.RED else scope.launch { ajustes.guardar(Ajustes.K.SERVIDOR_RED, false); if (servidor.activo) arrancar(false) }
            }
            servidor.direcciones.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            servidor.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            Text("También: /lista_<paquete>.m3u e /informe.txt. Con TiviMate en este mismo Google TV usa la dirección 127.0.0.1.", style = MaterialTheme.typography.bodySmall)
        }
        Tarjeta(titulo = "Subir a mi servidor (HTTP PUT o WebDAV)") {
            Text(subida?.let { "Destino: " + it.first.substringBefore('?') } ?: "Sin configurar")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Configurar") { dialogo = DialogoUtil.SUBIDA }
                BotonFoco("Subir ahora", habilitado = subida != null) { scope.launch { mensaje = repo.subirLista(app.exportador) } }
            }
            FilaInterruptor("Subir automáticamente tras cada sincronización", prefs?.get(Ajustes.K.SUBIR_AUTO) ?: false) {
                scope.launch { ajustes.guardar(Ajustes.K.SUBIR_AUTO, it) }
            }
        }
        Tarjeta(titulo = "Comprobar enlaces") {
            Text("Prueba las variantes de los paquetes de una en una (nunca en paralelo). Antes de cada prueba consulta las conexiones en uso: si alguien está viendo algo, se detiene. Mide el tiempo hasta el primer dato y la tasa de bits aproximada. No cambia nada sin tu confirmación.")
            FilaFoco(onClick = { dialogo = DialogoUtil.ESPERA }) {
                Text("Espera entre pruebas (provisional)", Modifier.weight(1f)); Text("${prefs?.get(Ajustes.K.ESPERA_ENLACES) ?: 10} s")
            }
            val ids = resultado?.canales.orEmpty().filter { it.tipoAmbito == TipoAmbito.PAQUETE }.flatMap { c -> c.variantes.map { it.stream.streamId } }
            Text("Variantes de paquetes: ${ids.size} · probadas: ${enlaces.size} (" +
                "${enlaces.values.count { it.estado == EstadoEnlace.FUNCIONA }} bien, ${enlaces.values.count { it.estado == EstadoEnlace.LENTO }} lentas, " +
                "${enlaces.values.count { it.estado == EstadoEnlace.FALLA }} fallan)")
            progreso?.let { Text(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!comprobando) BotonFoco("Comprobar las no probadas") {
                    comprobando = true; cancelar = false
                    app.alcance.launch {
                        mensaje = repo.comprobarEnlaces(ids.filter { it !in enlaces }) { cancelar }
                        comprobando = false
                    }
                } else BotonSecundario("Cancelar") { cancelar = true }
                BotonSecundario("Borrar resultados") { scope.launch { repo.borrarResultadosEnlaces() } }
            }
            FilaInterruptor("Ordenar las capas por tasa de bits medida", config.ordenarPorTasa) {
                scope.launch { ajustes.guardarConfigMotor(config.copy(ordenarPorTasa = it)); repo.recalcular() }
            }
            val fallan = enlaces.values.filter { it.estado == EstadoEnlace.FALLA }.map { it.streamId }.toSet()
            if (fallan.isNotEmpty()) BotonSecundario("Ocultar las ${fallan.size} variantes que fallan") {
                scope.launch {
                    resultado?.canales.orEmpty().flatMap { it.variantes }.filter { it.stream.streamId in fallan && !it.oculta }.forEach {
                        repo.decidir(decisionVariante(it, es.cazique.iptvgestor.core.TipoDecision.OCULTAR_VARIANTE))
                    }
                    mensaje = "Ocultadas (se puede deshacer en el historial)"
                }
            }
        }
        Tarjeta(titulo = "Bloqueo parental") {
            val activo = prefs?.get(Ajustes.K.PIN_ACTIVO) ?: true
            val hayPin = prefs?.get(Ajustes.K.PIN_HASH) != null
            Text(
                if (!activo) "Desactivado: el contenido para adultos se ve en la app."
                else if (!hayPin) "Activado: «FOR ADULTS» y los canales para adultos están ocultos en la app. Crea un PIN para poder verlos."
                else "Activado con PIN. En la lista exportada se respeta el ajuste «Incluir canales para adultos»."
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario(if (hayPin) "Cambiar PIN" else "Crear PIN") { dialogo = DialogoUtil.PIN_NUEVO }
                if (activo) BotonSecundario("Desactivar") { dialogo = DialogoUtil.PIN_QUITAR }
                else BotonSecundario("Activar") { scope.launch { ajustes.guardar(Ajustes.K.PIN_ACTIVO, true); ControlParental.desbloqueado.value = false } }
            }
        }
    }

    when (dialogo) {
        DialogoUtil.RED -> AlertDialog(
            onDismissRequest = { dialogo = DialogoUtil.NINGUNO },
            title = { Text("¿Abrir el servidor a la red local?") },
            text = { Text("La lista contiene tu usuario y contraseña. Cualquiera en tu red que conozca la ruta secreta podrá descargarla. Úsalo solo en una red de confianza.") },
            confirmButton = { TextButton(onClick = { dialogo = DialogoUtil.NINGUNO; scope.launch { ajustes.guardar(Ajustes.K.SERVIDOR_RED, true); if (servidor.activo) arrancar(true) } }) { Text("Abrir") } },
            dismissButton = { TextButton(onClick = { dialogo = DialogoUtil.NINGUNO }) { Text("Cancelar") } },
        )
        DialogoUtil.SUBIDA -> {
            var url by remember { mutableStateOf(subida?.first ?: "https://") }
            var u by remember { mutableStateOf(subida?.second ?: "") }
            var p by remember { mutableStateOf(subida?.third ?: "") }
            AlertDialog(
                onDismissRequest = { dialogo = DialogoUtil.NINGUNO },
                title = { Text("Servidor de subida") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(url, { url = it }, label = { Text("Carpeta (https://servidor/dav/iptv)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(u, { u = it }, label = { Text("Usuario (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(p, { p = it }, label = { Text("Contraseña (opcional)") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        Text("Se guarda cifrado. Usa HTTPS siempre que puedas.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialogo = DialogoUtil.NINGUNO
                        scope.launch { repo.credenciales.guardarSubida(url.trim(), u.trim(), p); subida = repo.credenciales.leerSubida() }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { dialogo = DialogoUtil.NINGUNO }) { Text("Cancelar") } },
            )
        }
        DialogoUtil.PIN_NUEVO -> DialogoPin("Nuevo PIN (4 a 8 cifras)", { dialogo = DialogoUtil.NINGUNO }) { pin ->
            dialogo = DialogoUtil.NINGUNO
            if (pin.length in 4..8 && pin.all(Char::isDigit)) scope.launch {
                ajustes.guardar(Ajustes.K.PIN_HASH, Pin.hash(pin)); ajustes.guardar(Ajustes.K.PIN_ACTIVO, true); mensaje = "PIN guardado"
            } else mensaje = "El PIN debe tener entre 4 y 8 cifras"
        }
        DialogoUtil.PIN_QUITAR -> DialogoPin("Introduce el PIN para desactivar", { dialogo = DialogoUtil.NINGUNO }) { pin ->
            dialogo = DialogoUtil.NINGUNO
            scope.launch {
                val h = ajustes.leer(Ajustes.K.PIN_HASH)
                if (h == null || Pin.verificar(pin, h)) { ajustes.guardar(Ajustes.K.PIN_ACTIVO, false); mensaje = "Bloqueo desactivado" }
                else mensaje = "PIN incorrecto"
            }
        }
        DialogoUtil.ESPERA -> DialogoOpciones("Espera entre pruebas", listOf(5, 10, 20, 30, 60).map { "$it" to "$it segundos" }, { dialogo = DialogoUtil.NINGUNO }) {
            dialogo = DialogoUtil.NINGUNO; scope.launch { ajustes.guardar(Ajustes.K.ESPERA_ENLACES, it.toInt()) }
        }
        DialogoUtil.NINGUNO -> Unit
    }
}

@Composable
fun DialogoPin(titulo: String, cerrar: () -> Unit, aceptar: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        },
        confirmButton = { TextButton(onClick = { aceptar(pin) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
