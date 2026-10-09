@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.CasoRevision
import es.cazique.iptvgestor.core.Decision
import es.cazique.iptvgestor.core.TipoCaso
import es.cazique.iptvgestor.core.TipoDecision
import kotlinx.coroutines.launch

/** Bandeja "Por revisar" (sección 5.2), con modo "revisar en tanda" y atajos del mando. */
@Composable
fun PantallaRevisar() {
    val app = LocalApp.current
    val repo = app.repositorio
    val nav = LocalNav.current
    val tv = LocalTv.current
    val casos by repo.casos.collectAsState()
    val resultado by repo.resultado.collectAsState()
    val scope = rememberCoroutineScope()
    var tipo by rememberSaveable { mutableStateOf<TipoCaso?>(null) }
    var tanda by rememberSaveable { mutableStateOf(false) }
    var indice by rememberSaveable { mutableStateOf(0) }
    var eligiendoEpg by remember { mutableStateOf<CasoRevision?>(null) }

    val visibles = casos.filter { tipo == null || it.tipo == tipo }
    fun saltar() { indice = (indice + 1).coerceAtMost((visibles.size - 1).coerceAtLeast(0)) }

    fun resolver(caso: CasoRevision, d: Decision?) = scope.launch {
        if (d != null) repo.decidir(d)
        if (!tanda) return@launch
        // La lista se recalcula sin el caso resuelto: el índice ya apunta al siguiente.
        indice = indice.coerceAtMost((repo.casos.value.filter { tipo == null || it.tipo == tipo }.size - 1).coerceAtLeast(0))
    }

    fun ignorar(caso: CasoRevision): Decision = when (caso.tipo) {
        TipoCaso.POSIBLE_DUPLICADO ->
            Decision(tipo = TipoDecision.NO_DUPLICADO, clave = caso.clave, valor = caso.sugerencias.firstOrNull()?.clave ?: "")
        TipoCaso.NUEVO -> Decision(tipo = TipoDecision.IGNORAR, clave = caso.id.substringAfter('|'), valor = caso.tipo.name)
        else -> Decision(tipo = TipoDecision.IGNORAR, clave = caso.clave, valor = caso.tipo.name)
    }

    /** Acción principal de cada caso. */
    fun confirmar(caso: CasoRevision) = scope.launch {
        when (caso.tipo) {
            TipoCaso.SIN_GUIA, TipoCaso.CONFIANZA_BAJA -> caso.sugerencias.firstOrNull()?.epg?.let {
                repo.decidir(Decision(tipo = TipoDecision.ASIGNAR_EPG, clave = caso.clave, valor = it.id))
            }
            TipoCaso.POSIBLE_DUPLICADO -> {
                val destino = caso.sugerencias.firstOrNull()?.clave
                if (destino != null) caso.canal?.variantes?.forEach { v -> repo.decidir(decisionVariante(v, TipoDecision.UNIR, destino)) }
            }
            TipoCaso.NUEVO -> repo.decidir(ignorar(caso))
            TipoCaso.DESAPARECIDO -> caso.decision?.let { repo.deshacer(it.id) }
        }
        resolver(caso, null)
    }

    val foco = remember { FocusRequester() }
    LaunchedEffect(tanda) { if (tanda && tv) runCatching { foco.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = if (tv) 32.dp else 12.dp, vertical = 8.dp)
            .onPreviewKeyEvent { e ->
                // Atajos del mando (mostrados en pantalla): avance rápido / canal + = siguiente; retroceso / canal - = anterior.
                if (!tanda || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaFastForward, Key.ChannelUp, Key.MediaNext, Key.N -> { saltar(); true }
                    Key.MediaRewind, Key.ChannelDown, Key.MediaPrevious, Key.P -> { indice = (indice - 1).coerceAtLeast(0); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Cabecera("Por revisar (${casos.size})", false, "Lo que la app no sabe resolver sola, con sugerencias") {}
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = tipo == null, onClick = { tipo = null; indice = 0 }, label = { Text("Todo") })
            TipoCaso.entries.forEach { t ->
                val n = casos.count { it.tipo == t }
                if (n > 0) FilterChip(selected = tipo == t, onClick = { tipo = t; indice = 0 }, label = { Text("${t.titulo} ($n)") })
            }
            FilterChip(selected = tanda, onClick = { tanda = !tanda; indice = 0 }, label = { Text("Revisar en tanda") }, modifier = Modifier.testTag("tanda"))
        }
        if (tanda) Text("Atajos: ⏩ / CH+ / N siguiente · ⏪ / CH− / P anterior · Centro: elegir botón", style = MaterialTheme.typography.bodySmall)
        if (visibles.isEmpty()) EstadoVacio(es.cazique.iptvgestor.R.drawable.ic_revisar, "Todo revisado",
            "No hay canales sin guía, dudosos ni duplicados pendientes.")
        if (tanda) {
            visibles.getOrNull(indice)?.let { caso ->
                Text("Caso ${indice + 1} de ${visibles.size}")
                TarjetaCaso(caso, Modifier.focusRequester(foco),
                    onConfirmar = { confirmar(caso) },
                    onElegir = { eligiendoEpg = caso },
                    onIgnorar = { resolver(caso, ignorar(caso)) },
                    onFicha = { caso.canal?.let { nav.ir(Destino.Ficha(it.ambito, it.clave)) } },
                    onSiguiente = { saltar() })
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibles, key = { it.id }) { caso ->
                    TarjetaCaso(caso, Modifier,
                        onConfirmar = { confirmar(caso) },
                        onElegir = { eligiendoEpg = caso },
                        onIgnorar = { resolver(caso, ignorar(caso)) },
                        onFicha = { caso.canal?.let { nav.ir(Destino.Ficha(it.ambito, it.clave)) } },
                        onSiguiente = null)
                }
            }
        }
    }
    eligiendoEpg?.let { caso ->
        SelectorEpg(resultado?.emparejador, caso.clave, { eligiendoEpg = null }) { e ->
            eligiendoEpg = null
            resolver(caso, Decision(tipo = TipoDecision.ASIGNAR_EPG, clave = caso.clave, valor = e.id))
        }
    }
}

@Composable
private fun TarjetaCaso(
    caso: CasoRevision,
    modifier: Modifier,
    onConfirmar: () -> Unit,
    onElegir: () -> Unit,
    onIgnorar: () -> Unit,
    onFicha: () -> Unit,
    onSiguiente: (() -> Unit)?,
) {
    Tarjeta(modifier) {
        Etiqueta(caso.tipo.titulo, MaterialTheme.colorScheme.secondary)
        Text(caso.descripcion, style = MaterialTheme.typography.titleMedium)
        caso.sugerencias.forEachIndexed { i, s ->
            Text("${if (i == 0) "Sugerencia" else "Otra"}: ${s.texto} (${(s.confianza * 100).toInt()} %)", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val textoConfirmar = when (caso.tipo) {
                TipoCaso.SIN_GUIA, TipoCaso.CONFIANZA_BAJA -> if (caso.sugerencias.isNotEmpty()) "Confirmar sugerencia" else null
                TipoCaso.POSIBLE_DUPLICADO -> "Sí, unir"
                TipoCaso.NUEVO -> "Visto"
                TipoCaso.DESAPARECIDO -> "Descartar decisión"
            }
            textoConfirmar?.let { BotonFoco(it, Modifier.testTag("confirmar")) { onConfirmar() } }
            if (caso.tipo == TipoCaso.SIN_GUIA || caso.tipo == TipoCaso.CONFIANZA_BAJA) BotonSecundario("Elegir otra") { onElegir() }
            if (caso.tipo == TipoCaso.POSIBLE_DUPLICADO) BotonSecundario("No, son distintos") { onIgnorar() }
            else BotonSecundario("Ignorar") { onIgnorar() }
            if (caso.canal != null) BotonSecundario("Ficha") { onFicha() }
            onSiguiente?.let { BotonSecundario("Siguiente →") { it() } }
        }
    }
}
