@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.EntradaLista
import es.cazique.iptvgestor.core.Normalizacion

enum class FiltroLista(val texto: String) { TODOS("Todos"), SIN_GUIA("Sin guía"), MANUALES("Decididos a mano"), CON_GUIA("Con guía") }

/** Vista previa de la lista final, en el orden de exportación (sección 6.2.3). */
@Composable
fun PantallaLista() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val tv = LocalTv.current
    val resultado by app.repositorio.resultado.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf(FiltroLista.TODOS) }
    var grupo by rememberSaveable { mutableStateOf<String?>(null) }

    val lista = resultado?.lista.orEmpty()
    val grupos = remember(lista) { lista.map { it.grupoSalida }.distinct() }
    val filtrada = remember(lista, q, filtro, grupo) {
        val qq = Normalizacion.clave(q)
        lista.filter { e ->
            (grupo == null || e.grupoSalida == grupo) &&
                (qq.isEmpty() || e.canal.clave.contains(qq) || Normalizacion.clave(e.variante.stream.nombre).contains(qq) || e.canal.tvgId.uppercase().contains(qq)) &&
                when (filtro) {
                    FiltroLista.TODOS -> true
                    FiltroLista.SIN_GUIA -> e.canal.emparejado == null
                    FiltroLista.CON_GUIA -> e.canal.emparejado != null
                    FiltroLista.MANUALES -> e.canal.manual
                }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = if (tv) 32.dp else 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Cabecera("Vista previa (${filtrada.size} de ${lista.size})", false) {}
        OutlinedTextField(q, { q = it }, label = { Text("Buscar canal o ID de guía") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("buscar_lista"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FiltroLista.entries.forEach { f -> FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(f.texto) }) }
            FilterChip(selected = grupo != null, onClick = { grupo = if (grupo == null) grupos.firstOrNull() else null }, label = { Text(grupo ?: "Todos los grupos") })
        }
        if (grupo != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                grupos.take(60).forEach { g -> FilterChip(selected = g == grupo, onClick = { grupo = g }, label = { Text(g, maxLines = 1) }) }
            }
        }
        if (lista.isEmpty()) Text("Todavía no hay datos. Sincroniza desde Resumen o importa archivos en Cuenta.")
        LazyColumn(Modifier.fillMaxSize().testTag("lista")) {
            var anterior: String? = null
            val filas = ArrayList<Pair<String?, EntradaLista>>()
            for (e in filtrada) { filas.add((if (e.grupoSalida != anterior) e.grupoSalida else null) to e); anterior = e.grupoSalida }
            items(filas, key = { it.second.grupoSalida + "|" + it.second.variante.stream.streamId }) { (cabecera, e) ->
                Column {
                    cabecera?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                    FilaFoco(onClick = { nav.ir(Destino.Ficha(e.canal.ambito, e.canal.clave)) }) {
                        IconoCanal(e.canal.icono)
                        Column(Modifier.weight(1f)) {
                            Text(e.nombreMostrado, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (e.canal.tvgId.isEmpty()) "⚠ Sin guía" else "Guía: ${e.canal.tvgId}") +
                                    (e.capa?.let { " · capa $it" } ?: "") + (if (e.canal.manual) " · ✎ manual" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.canal.tvgId.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
