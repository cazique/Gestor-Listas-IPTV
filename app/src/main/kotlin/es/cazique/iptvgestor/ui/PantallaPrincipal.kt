package es.cazique.iptvgestor.ui

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.IptvGestorApp
import es.cazique.iptvgestor.R
import es.cazique.iptvgestor.actualizacion.EstadoActualizacion
import es.cazique.iptvgestor.datos.Ajustes
import kotlinx.coroutines.launch


@Composable
fun PantallaPrincipal(app: IptvGestorApp, tv: Boolean, buscarAlAbrir: Boolean, abrirIntent: (Intent) -> Unit) {
    val gestor = app.actualizaciones
    val estado by gestor.estado.collectAsState()
    val nota by app.ajustes.notaPrueba.collectAsState(initial = "")
    val canalPruebas by app.ajustes.canalPruebas.collectAsState(initial = false)
    val auto by app.ajustes.comprobarAuto.collectAsState(initial = true)
    var actualizadaA by remember { mutableStateOf<String?>(null) }
    var notaEditada by remember(nota) { mutableStateOf(nota) }
    val scope = rememberCoroutineScope()
    val foco = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        actualizadaA = gestor.versionRecienActualizada()
        if (buscarAlAbrir) gestor.comprobar() else gestor.comprobarSiToca()
        if (tv) runCatching { foco.requestFocus() }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(if (tv) 48.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.version, gestor.versionName, gestor.versionCode),
                modifier = Modifier.testTag("version"),
            )
            actualizadaA?.let {
                Card(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.actualizada_a, it), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.secondary)
                }
            }
            BotonFoco(
                texto = stringResource(R.string.buscar_actualizaciones),
                modifier = Modifier.focusRequester(foco).testTag("buscar"),
                habilitado = estado !is EstadoActualizacion.Buscando && estado !is EstadoActualizacion.Descargando,
            ) { scope.launch { gestor.comprobar() } }

            when (val e = estado) {
                EstadoActualizacion.Buscando -> Text(stringResource(R.string.buscando))
                EstadoActualizacion.AlDia -> Text(stringResource(R.string.al_dia))
                is EstadoActualizacion.Descargando -> {
                    Text(stringResource(R.string.descargando, e.porcentaje))
                    LinearProgressIndicator(progress = { e.porcentaje / 100f }, modifier = Modifier.fillMaxWidth())
                }
                EstadoActualizacion.Verificando -> Text(stringResource(R.string.verificando))
                EstadoActualizacion.Instalando -> Text(stringResource(R.string.instalando))
                is EstadoActualizacion.Error -> Text(stringResource(R.string.error, e.mensaje), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }

            Spacer(Modifier.padding(4.dp))
            FilaInterruptor(stringResource(R.string.comprobar_auto), auto) { v -> scope.launch { app.ajustes.guardar(Ajustes.K.COMPROBAR_AUTO, v) } }
            FilaInterruptor(stringResource(R.string.canal_pruebas), canalPruebas) { v -> scope.launch { app.ajustes.guardar(Ajustes.K.CANAL_PRUEBAS, v) } }
            OutlinedTextField(
                value = notaEditada,
                onValueChange = { notaEditada = it },
                label = { Text(stringResource(R.string.nota_prueba)) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).testTag("nota"),
                singleLine = true,
            )
            BotonFoco(stringResource(R.string.aceptar)) { scope.launch { app.ajustes.guardar(Ajustes.K.NOTA_PRUEBA, notaEditada) } }
        }
    }

    (estado as? EstadoActualizacion.Disponible)?.let { d ->
        AlertDialog(
            onDismissRequest = { gestor.limpiarEstado() },
            title = { Text(stringResource(R.string.hay_version, d.info.versionName)) },
            text = { Text(d.info.releaseNotes.ifBlank { "—" }) },
            confirmButton = { Button(onClick = { scope.launch { gestor.descargarEInstalar(d.info) } }) { Text(stringResource(R.string.actualizar)) } },
            dismissButton = { TextButton(onClick = { gestor.limpiarEstado() }) { Text(stringResource(R.string.mas_tarde)) } },
        )
    }
    (estado as? EstadoActualizacion.NecesitaPermiso)?.let { p ->
        AlertDialog(
            onDismissRequest = { gestor.limpiarEstado() },
            title = { Text(stringResource(R.string.permiso_instalar_titulo)) },
            text = { Text(stringResource(R.string.permiso_instalar_texto)) },
            confirmButton = { Button(onClick = { gestor.abrirAjustesPermiso(abrirIntent) }) { Text(stringResource(R.string.abrir_ajustes)) } },
            dismissButton = {
                TextButton(onClick = {
                    if (gestor.puedeInstalar()) gestor.instalarOPedirPermiso(p.apk) else gestor.limpiarEstado()
                }) { Text(stringResource(R.string.aceptar)) }
            },
        )
    }
}

@Composable
fun BotonFoco(texto: String, modifier: Modifier = Modifier, habilitado: Boolean = true, onClick: () -> Unit) {
    var enfocado by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier
            .onFocusChanged { enfocado = it.isFocused }
            .then(if (enfocado) Modifier.border(3.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(24.dp)) else Modifier),
    ) { Text(texto) }
}

@Composable
fun FilaInterruptor(texto: String, valor: Boolean, cambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(texto, Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = cambiar)
    }
}
