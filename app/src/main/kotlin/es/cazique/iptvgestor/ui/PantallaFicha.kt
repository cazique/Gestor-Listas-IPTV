@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.CanalLogico
import es.cazique.iptvgestor.core.Decision
import es.cazique.iptvgestor.core.Normalizacion
import es.cazique.iptvgestor.core.TipoDecision
import es.cazique.iptvgestor.core.Variante
import kotlinx.coroutines.launch

/** Decisión sobre una variante concreta, con sus dos identidades (sección 5.3). */
fun decisionVariante(v: Variante, tipo: TipoDecision, valor: String = "", ambito: String = "") = Decision(
    tipo = tipo, streamId = v.stream.streamId, nombre = Normalizacion.nombreNormalizado(v.stream.nombre),
    clave = v.claveOriginal, grupo = v.grupo, valor = valor, ambito = ambito,
)

fun decisionCanal(c: CanalLogico, tipo: TipoDecision, valor: String = "") =
    Decision(tipo = tipo, clave = c.clave, ambito = c.ambito, valor = valor)

private enum class Dialogo { NINGUNO, EPG, ICONO, UNIR, PAQUETE }

/** Ficha de un canal lógico: variantes, guía y acciones manuales (sección 5.1). */
@Composable
fun PantallaFicha(ambito: String, clave: String) {
    val app = LocalApp.current
    val repo = app.repositorio
    val nav = LocalNav.current
    val resultado by repo.resultado.collectAsState()
    val scope = rememberCoroutineScope()
    var dialogo by remember { mutableStateOf(Dialogo.NINGUNO) }
    var variante by remember { mutableStateOf<Variante?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    val r = resultado
    val canal = r?.canales?.firstOrNull { it.ambito == ambito && it.clave == clave }
        ?: r?.canales?.firstOrNull { it.clave == clave }
    fun decidir(d: Decision, texto: String) = scope.launch { repo.decidir(d); mensaje = "$texto (se puede deshacer en Historial)" }

    PantallaBase(clave, conAtras = true) {
        if (canal == null) { Text("El canal ya no existe en la lista (quizá cambió por una decisión)."); return@PantallaBase }
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconoCanal(canal.icono, 64)
                Column {
                    Text(canal.clave, style = MaterialTheme.typography.titleMedium)
                    Text("Ámbito: ${canal.ambito}" + if (canal.oculto) " · OCULTO" else "")
                    Text(canal.emparejado?.let { "Guía: ${it.canal.id} (${it.metodo.name.lowercase()}, ${(it.confianza * 100).toInt()} %)" } ?: "⚠ Sin guía",
                        color = if (canal.emparejado == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonSecundario("Asignar guía") { dialogo = Dialogo.EPG }
                if (canal.emparejado != null) BotonSecundario("Quitar guía") { decidir(decisionCanal(canal, TipoDecision.QUITAR_EPG), "Guía quitada") }
                BotonSecundario("Cambiar icono") { dialogo = Dialogo.ICONO }
                BotonSecundario(if (canal.oculto) "Mostrar canal" else "Ocultar canal") {
                    scope.launch {
                        if (canal.oculto) {
                            repo.decisiones().filter { it.activa && it.tipo == TipoDecision.OCULTAR_CANAL && it.clave == canal.clave }.forEach { repo.deshacer(it.id) }
                            mensaje = "Canal visible"
                        } else { repo.decidir(decisionCanal(canal, TipoDecision.OCULTAR_CANAL)); mensaje = "Canal oculto" }
                    }
                }
            }
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        }
        Text("Variantes (${canal.variantes.size}), de la capa 1 en adelante", style = MaterialTheme.typography.titleMedium)
        canal.variantes.forEachIndexed { i, v ->
            Tarjeta {
                Text("${i + 1}. ${v.stream.nombre.trim()}" + (if (v.preferida) " ★" else "") + (if (v.oculta) " · oculta" else ""))
                Text("Grupo: ${v.grupo} · calidad ${Normalizacion.PERFIL_NORMAL.getOrElse(v.puntos % 10) { "?" }}" +
                    (if (v.puntos >= 10) " (solo eventos/HDR)" else "") + (if (v.respaldo) " · BK" else "") + " · id ${v.stream.streamId}",
                    style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!v.preferida) BotonSecundario("Preferida") { decidir(decisionVariante(v, TipoDecision.PREFERIDA), "Variante preferida") }
                    BotonSecundario(if (v.oculta) "Mostrar" else "Ocultar") {
                        scope.launch {
                            if (v.oculta) {
                                repo.decisiones().filter { it.activa && it.tipo == TipoDecision.OCULTAR_VARIANTE && it.streamId == v.stream.streamId }.forEach { repo.deshacer(it.id) }
                            } else repo.decidir(decisionVariante(v, TipoDecision.OCULTAR_VARIANTE))
                        }
                    }
                    if (canal.variantes.size > 1) BotonSecundario("Separar") { decidir(decisionVariante(v, TipoDecision.SEPARAR), "Variante separada") }
                    BotonSecundario("Unir con…") { variante = v; dialogo = Dialogo.UNIR }
                    BotonSecundario("Paquete…") { variante = v; dialogo = Dialogo.PAQUETE }
                }
            }
        }
        HorizontalDivider()
    }

    when (dialogo) {
        Dialogo.EPG -> SelectorEpg(r?.emparejador, canal?.clave ?: clave, { dialogo = Dialogo.NINGUNO }) { e ->
            dialogo = Dialogo.NINGUNO
            canal?.let { decidir(decisionCanal(it, TipoDecision.ASIGNAR_EPG, e.id), "Guía asignada: ${e.id}") }
        }
        Dialogo.ICONO -> DialogoTexto("Cambiar icono", "URL del icono (vacío = el de la guía)", canal?.icono ?: "", { dialogo = Dialogo.NINGUNO }) { url ->
            dialogo = Dialogo.NINGUNO
            canal?.let { decidir(decisionCanal(it, TipoDecision.ICONO, url), "Icono cambiado") }
        }
        Dialogo.UNIR -> {
            val opciones = r?.canales.orEmpty().filter { it.ambito == canal?.ambito && it.clave != canal?.clave }
                .map { it.clave to it.clave }.distinct().sortedBy { it.first }
            DialogoOpciones("Unir con otro canal de ${canal?.ambito}", opciones, { dialogo = Dialogo.NINGUNO }) { destino ->
                dialogo = Dialogo.NINGUNO
                variante?.let { decidir(decisionVariante(it, TipoDecision.UNIR, destino), "Unida con $destino") }
            }
        }
        Dialogo.PAQUETE -> {
            val paquetes = listOf("" to "Sin paquete (su grupo original)") +
                (r?.cifras?.paquetes.orEmpty().map { it.nombre to it.nombre })
            DialogoOpciones("Cambiar de paquete", paquetes, { dialogo = Dialogo.NINGUNO }) { p ->
                dialogo = Dialogo.NINGUNO
                variante?.let { decidir(decisionVariante(it, TipoDecision.CAMBIAR_PAQUETE, p), "Paquete cambiado") }
            }
        }
        Dialogo.NINGUNO -> Unit
    }
    if (canal == null && nav.pila.size > 1 && r != null && r.canales.isEmpty()) nav.atras()
}
