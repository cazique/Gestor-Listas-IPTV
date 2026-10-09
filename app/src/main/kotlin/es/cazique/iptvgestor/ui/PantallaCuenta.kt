@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.Cuenta
import es.cazique.iptvgestor.datos.ResultadoConexion
import kotlinx.coroutines.launch

/** Configuración inicial: host, usuario y contraseña, y "Probar conexión" (sección 6.2.1). */
@Composable
fun PantallaCuenta() {
    val app = LocalApp.current
    val repo = app.repositorio
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var resultado by remember { mutableStateOf<ResultadoConexion?>(null) }
    var pidiendoHttp by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var streamsUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        repo.credenciales.leer()?.let { host = it.host; usuario = it.usuario; contrasena = it.contrasena }
    }

    fun probar(permitirHttp: Boolean) {
        ocupado = true
        scope.launch {
            val c = Cuenta(host.trim(), usuario.trim(), contrasena)
            val r = repo.probarConexion(c, permitirHttp)
            resultado = r
            when (r) {
                is ResultadoConexion.Ok -> { repo.guardarCuenta(c, r.base); mensaje = "Cuenta guardada" }
                is ResultadoConexion.NecesitaHttp -> pidiendoHttp = r.host
                is ResultadoConexion.Error -> Unit
            }
            ocupado = false
        }
    }

    val elegirCategorias = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { cats ->
        val s = streamsUri
        if (cats != null && s != null) scope.launch {
            val r = repo.importarArchivos(s, cats)
            mensaje = r.error?.let { "Error al importar: $it" } ?: "Importado. Ve a Resumen o a Lista."
        }
    }
    val elegirStreams = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { streamsUri = uri; elegirCategorias.launch(arrayOf("application/json", "text/plain", "*/*")) }
    }

    PantallaBase("Cuenta del proveedor", conAtras = true) {
        Text("Los datos se guardan cifrados en el dispositivo (Android Keystore) y nunca salen de él, salvo dentro de la lista que exportes.")
        OutlinedTextField(host, { host = it }, label = { Text("Servidor (por ejemplo, servidor.com:8080)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().testTag("host"))
        OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("usuario"))
        OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("contrasena"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonFoco("Probar conexión y guardar", habilitado = !ocupado && host.isNotBlank() && usuario.isNotBlank()) { probar(false) }
        }
        when (val r = resultado) {
            is ResultadoConexion.Ok -> Tarjeta {
                Text(if (r.https) "Conectado por HTTPS" else "Conectado por HTTP (sin cifrar)", style = MaterialTheme.typography.titleMedium)
                Dato("Estado", r.estado.estado)
                Dato("Caducidad", r.estado.caducidad?.let { fechaCorta(it * 1000) } ?: "—")
                Dato("Conexiones máximas", r.estado.conexionesMaximas?.toString() ?: "—")
                Dato("Conexiones en uso", r.estado.conexionesActivas?.toString() ?: "—")
            }
            is ResultadoConexion.Error -> Text("Error: ${r.mensaje}", color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Tarjeta {
            Text("Arrancar sin red", style = MaterialTheme.typography.titleMedium)
            Text("Importa primero live.json (get_live_streams) y después el archivo de categorías (get_live_categories).")
            BotonSecundario("Importar archivos") { elegirStreams.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
    }

    pidiendoHttp?.let { h ->
        AlertDialog(
            onDismissRequest = { pidiendoHttp = null },
            title = { Text("¿Usar HTTP sin cifrar?") },
            text = { Text("El servidor $h no responde por HTTPS. Por HTTP, el usuario y la contraseña viajan sin cifrar y cualquiera en la red podría verlos. ¿Permitir HTTP para este servidor? Se recordará tu decisión.") },
            confirmButton = { TextButton(onClick = { pidiendoHttp = null; probar(true) }) { Text("Permitir HTTP") } },
            dismissButton = { TextButton(onClick = { pidiendoHttp = null }) { Text("Cancelar") } },
        )
    }
}
