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
import kotlinx.coroutines.launch

enum class FiltroLista(val texto: String) { TODOS("Todos"), SIN_GUIA("Sin guía"), MANUALES("Decididos a mano"), CON_GUIA("Con guía") }

/** Vista previa de la lista final, en el orden de exportación (sección 6.2.3). */
@Composable
fun PantallaLista() {
    PantallaListaInterna()
}

@Composable
private fun PantallaListaInterna() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val tv = LocalTv.current
    val resultado by app.repositorio.resultado.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf(FiltroLista.TODOS) }
    var grupo by rememberSaveable { mutableStateOf<String?>(null) }

    val prefs by app.ajustes.datos.collectAsState(initial = null)
    val desbloqueado by ControlParental.desbloqueado.collectAsState()
    val bloquear = (prefs?.get(es.cazique.iptvgestor.datos.Ajustes.K.PIN_ACTIVO) ?: true) && !desbloqueado
    var pidiendoPin by remember { mutableStateOf(false) }
    val todas = resultado?.lista.orEmpty()
    val lista = remember(todas, bloquear) { if (bloquear) todas.filterNot { it.canal.adulto } else todas }
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
        Cabecera("Vista previa", false, "${filtrada.size} de ${lista.size} canales, en el orden en que se exportarán") {}
        OutlinedTextField(
            q, { q = it }, label = { Text("Buscar canal o ID de guía") }, singleLine = true,
            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(es.cazique.iptvgestor.R.drawable.ic_buscar), null) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("buscar_lista"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FiltroLista.entries.forEach { f -> FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(f.texto) }) }
            FilterChip(selected = grupo != null, onClick = { grupo = if (grupo == null) grupos.firstOrNull() else null }, label = { Text(grupo ?: "Todos los grupos") })
        }
        if (grupo != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                grupos.take(60).forEach { g -> FilterChip(selected = g == grupo, onClick = { grupo = g }, label = { Text(g, maxLines = 1) }) }
            }
        }
        if (bloquear && todas.size != lista.size) {
            FilaFoco(onClick = { pidiendoPin = true }) { Text("🔒 ${todas.size - lista.size} canales para adultos ocultos. Toca para desbloquear con el PIN.") }
        }
        if (lista.isEmpty()) EstadoVacio(es.cazique.iptvgestor.R.drawable.ic_lista, "La lista está vacía",
            "Sincroniza desde Resumen o importa los archivos en Ajustes → Cuenta.")
        else if (filtrada.isEmpty()) EstadoVacio(es.cazique.iptvgestor.R.drawable.ic_buscar, "Sin resultados", "Prueba con otro texto o quita los filtros.")
        LazyColumn(Modifier.fillMaxSize().testTag("lista")) {
            var anterior: String? = null
            val filas = ArrayList<Pair<String?, EntradaLista>>()
            for (e in filtrada) { filas.add((if (e.grupoSalida != anterior) e.grupoSalida else null) to e); anterior = e.grupoSalida }
            items(filas, key = { it.second.grupoSalida + "|" + it.second.variante.stream.streamId }) { (cabecera, e) ->
                Column {
                    cabecera?.let {
                        androidx.compose.material3.Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                        ) {
                            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    FilaFoco(onClick = { nav.ir(Destino.Ficha(e.canal.ambito, e.canal.clave)) }) {
                        IconoCanal(e.canal.icono, 44)
                        Column(Modifier.weight(1f)) {
                            Text(e.nombreMostrado, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (e.canal.tvgId.isEmpty()) "Sin guía" else e.canal.tvgId,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.canal.tvgId.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (e.canal.manual) Etiqueta("manual", MaterialTheme.colorScheme.secondary)
                        e.capa?.let { Etiqueta("capa $it") }
                    }
                }
            }
        }
    }
    if (pidiendoPin) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        DialogoPin("PIN parental", { pidiendoPin = false }) { pin ->
            pidiendoPin = false
            scope.launch {
                val h = app.ajustes.leer(es.cazique.iptvgestor.datos.Ajustes.K.PIN_HASH)
                if (h != null && es.cazique.iptvgestor.core.Pin.verificar(pin, h)) ControlParental.desbloqueado.value = true
            }
        }
    }
}
