@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.R
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

    val ultima = prefs?.get(Ajustes.K.ULTIMA_SINCRONIZACION)
    val estado = prefs?.get(Ajustes.K.ESTADO_SINCRONIZACION)
    val conError = estado?.startsWith("error") == true

    PantallaBase("Gestor IPTV", subtitulo = "Tu lista limpia, ordenada y con guía") {
        if (hayCuenta == false) {
            Tarjeta(titulo = "Empieza aquí", icono = R.drawable.ic_ajustes, destacada = true) {
                Text("Configura la cuenta del proveedor (o importa live.json y las categorías) para crear tu lista.")
                Button(onClick = { nav.ir(Destino.Cuenta) }) { Text("Configurar cuenta") }
            }
        }

        Tarjeta(titulo = "Sincronización", icono = R.drawable.ic_sincronizar) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    progreso != null -> Etiqueta("En curso", MaterialTheme.colorScheme.secondary)
                    conError -> Etiqueta("Con error", MaterialTheme.colorScheme.error)
                    ultima != null -> Etiqueta("Al día", MaterialTheme.colorScheme.tertiary)
                    else -> Etiqueta("Sin sincronizar", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Última: ${fechaCorta(ultima)}", style = MaterialTheme.typography.bodySmall)
            }
            if (conError) Text(estado.orEmpty().removePrefix("error: "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("Guía de dobleM: ${fechaCorta(prefs?.get(Ajustes.K.ULTIMA_EPG))}", style = MaterialTheme.typography.bodySmall)
            progreso?.let { Text(it); LinearProgressIndicator(Modifier.fillMaxWidth().testTag("progreso")) }
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val r = repo.sincronizar()
                            mensaje = r.error?.let { "Error: $it" } ?: r.informe?.let {
                                val auto = app.exportador.exportarAuto()
                                "Hecho: ${it.anadidos.size} añadidos, ${it.quitados.size} quitados, ${it.cambiados.size} cambiados" +
                                    (auto?.let { e -> ". Exportación automática: $e" } ?: "")
                            }
                        }
                    },
                    enabled = progreso == null,
                    modifier = Modifier.focusRequester(foco).testTag("sincronizar"),
                ) {
                    Icon(painterResource(R.drawable.ic_sincronizar), null, Modifier.size(18.dp))
                    Text("  Sincronizar ahora")
                }
                BotonSecundario("Actualizar guía") { scope.launch { mensaje = repo.actualizarEpg() ?: "Guía actualizada" } }
                BotonSecundario("Informe de cambios") { nav.ir(Destino.Informes) }
            }
        }

        val r = resultado
        if (r != null && r.cifras.canalesOrigen > 0) {
            val c = r.cifras
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val ancho = Modifier.widthIn(min = if (tv) 200.dp else 150.dp)
                Cifra("%,d".format(c.entradasLista), "canales en la lista final", ancho)
                Cifra("${c.gruposLista}", "grupos", ancho)
                Cifra("${c.conGuia}", "canales con guía (de ${c.canalesLogicos})", ancho, MaterialTheme.colorScheme.tertiary)
                Cifra("${casos.size}", "por revisar", ancho.width(if (tv) 200.dp else 150.dp),
                    if (casos.isEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
            }
            Tarjeta(titulo = "Paquetes y capas", icono = R.drawable.ic_lista) {
                c.paquetes.forEach { p ->
                    Text(p.nombre, style = MaterialTheme.typography.titleMedium)
                    Text("${p.canales} canales · ${p.entradas} entradas · ${p.conGuia} con guía", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.capas.forEachIndexed { i, n -> Etiqueta("${p.nombre} ${i + 1}: $n") }
                    }
                }
            }
            Tarjeta(titulo = "Origen", icono = R.drawable.ic_tv) {
                Dato("Canales del proveedor", "%,d".format(c.canalesOrigen))
                Dato("Grupos del proveedor", "${c.gruposOrigen}")
                Dato("Conservados", "%,d en %d grupos".format(c.entradasConservadas, c.gruposConservados))
                Dato("Separadores descartados", "${c.separadores}")
            }
        } else if (hayCuenta == true) {
            EstadoVacio(R.drawable.ic_sincronizar, "Aún no hay canales", "Pulsa «Sincronizar ahora» para descargar la lista del proveedor y la guía.")
        }

        Tarjeta(titulo = "Accesos directos") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Por revisar (${casos.size})") { nav.seccion(Seccion.REVISAR) }
                BotonSecundario("Vista previa") { nav.seccion(Seccion.LISTA) }
                BotonSecundario("Exportar") { nav.seccion(Seccion.EXPORTAR) }
                BotonSecundario("Cuenta") { nav.ir(Destino.Cuenta) }
            }
        }
    }
}
