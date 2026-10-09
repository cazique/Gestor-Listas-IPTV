@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.datos.Ajustes
import kotlinx.coroutines.launch

@Composable
fun PantallaResumen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val tv = LocalTv.current
    val repo = app.repositorio
    val resultado by repo.resultado.collectAsState()
    val casos by repo.casos.collectAsState()
    val progreso by repo.progreso.collectAsState()
    val prefs by app.ajustes.datos.collectAsState(initial = null)
    var hayCuenta by remember { mutableStateOf<Boolean?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val foco = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        hayCuenta = repo.credenciales.leer() != null
        if (tv) runCatching { foco.requestFocus() }
    }

    PantallaBase("Resumen") {
        if (hayCuenta == false) {
            Tarjeta {
                Text("Configura la cuenta del proveedor para empezar (o importa live.json y las categorías).")
                Button(onClick = { nav.ir(Destino.Cuenta) }) { Text("Configurar cuenta") }
            }
        }
        val ultima = prefs?.get(Ajustes.K.ULTIMA_SINCRONIZACION)
        val estado = prefs?.get(Ajustes.K.ESTADO_SINCRONIZACION)
        Tarjeta {
            Text("Sincronización", style = MaterialTheme.typography.titleMedium)
            Dato("Última", fechaCorta(ultima))
            Dato("Estado", estado ?: "—")
            Dato("Guía actualizada", fechaCorta(prefs?.get(Ajustes.K.ULTIMA_EPG)))
            progreso?.let { Text(it); LinearProgressIndicator(Modifier.testTag("progreso")) }
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonFoco("Sincronizar ahora", Modifier.focusRequester(foco).testTag("sincronizar"), habilitado = progreso == null) {
                    scope.launch {
                        val r = repo.sincronizar()
                        mensaje = r.error?.let { "Error: $it" } ?: r.informe?.let {
                            val auto = app.exportador.exportarAuto()
                            "Hecho. Añadidos ${it.anadidos.size}, quitados ${it.quitados.size}, cambiados ${it.cambiados.size}" +
                                (auto?.let { e -> ". Exportación automática: $e" } ?: "")
                        }
                    }
                }
                BotonSecundario("Actualizar guía") { scope.launch { mensaje = repo.actualizarEpg() ?: "Guía actualizada" } }
                BotonSecundario("Informe de cambios") { nav.ir(Destino.Informes) }
            }
        }
        val r = resultado
        if (r != null && r.cifras.canalesOrigen > 0) {
            val c = r.cifras
            Tarjeta {
                Text("Cifras", style = MaterialTheme.typography.titleMedium)
                Dato("Canales de origen", "%,d".format(c.canalesOrigen))
                Dato("Grupos de origen", "${c.gruposOrigen}")
                Dato("Conservados", "%,d en %d grupos".format(c.entradasConservadas, c.gruposConservados))
                Dato("En la lista final", "%,d en %d grupos".format(c.entradasLista, c.gruposLista))
                Dato("Canales con guía", "${c.conGuia} de ${c.canalesLogicos}")
            }
            Tarjeta {
                Text("Paquetes y capas", style = MaterialTheme.typography.titleMedium)
                c.paquetes.forEach { p ->
                    Dato(p.nombre, "${p.canales} canales · ${p.entradas} entradas · guía ${p.conGuia}")
                    Text("Capas: " + p.capas.mapIndexed { i, n -> "${p.nombre} ${i + 1}: $n" }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Tarjeta {
            Text("Accesos directos", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Por revisar (${casos.size})") { nav.seccion(Seccion.REVISAR) }
                BotonSecundario("Vista previa") { nav.seccion(Seccion.LISTA) }
                BotonSecundario("Exportar") { nav.seccion(Seccion.EXPORTAR) }
                BotonSecundario("Cuenta") { nav.ir(Destino.Cuenta) }
            }
        }
    }
}
