@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package es.cazique.iptvgestor.ui

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.cazique.iptvgestor.core.MotorVod
import es.cazique.iptvgestor.core.Normalizacion
import es.cazique.iptvgestor.core.OrdenVod
import kotlinx.coroutines.launch

/** VOD (Fase 4): películas filtradas por categoría, sin duplicados, y series. Gestión, sin reproducir. */
@Composable
fun PantallaVod(abrirIntent: (Intent) -> Unit) {
    val app = LocalApp.current
    val vod = app.vod
    val tv = LocalTv.current
    val nav = LocalNav.current
    val datos by vod.datos.collectAsState()
    val scope = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var series by rememberSaveable { mutableStateOf(false) }
    var orden by rememberSaveable { mutableStateOf(OrdenVod.VALORACION) }
    var q by rememberSaveable { mutableStateOf("") }
    var seleccion by remember { mutableStateOf<Set<String>?>(null) }
    var ocultas by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val desbloqueado by ControlParental.desbloqueado.collectAsState()

    LaunchedEffect(Unit) { if (vod.datos.value == null) vod.cargar(); ocultas = vod.ocultas() }
    val d = datos
    // Por defecto, las categorías con prefijo de España ("ES", "ES|", "ES -"...).
    val categorias = d?.categorias.orEmpty()
    val conservadas = seleccion ?: categorias.filter { Normalizacion.limpio(it.nombre).startsWith("ES") }.map { it.id }.toSet()
    val titulos = remember(d, conservadas, orden, ocultas, desbloqueado) {
        d?.let { MotorVod.procesar(it.peliculas, conservadas, incluirAdultos = desbloqueado, orden = orden, ocultas = ocultas) }.orEmpty()
    }
    val qq = Normalizacion.clave(q)
    val visibles = if (qq.isEmpty()) titulos else titulos.filter { it.titulo.contains(qq) }

    Column(Modifier.fillMaxSize().padding(horizontal = if (tv) 32.dp else 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Cabecera("Películas y series", true) { nav.atras() }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonFoco(if (ocupado) "Sincronizando…" else "Sincronizar VOD", habilitado = !ocupado) {
                ocupado = true
                scope.launch { mensaje = vod.sincronizar(); ocupado = false }
            }
            FilterChip(selected = !series, onClick = { series = false }, label = { Text("Películas (${d?.peliculas?.size ?: 0})") })
            FilterChip(selected = series, onClick = { series = true }, label = { Text("Series (${d?.series?.size ?: 0})") })
        }
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        OutlinedTextField(q, { q = it }, label = { Text("Buscar título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (!series) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OrdenVod.entries.forEach { o ->
                    FilterChip(selected = orden == o, onClick = { orden = o },
                        label = { Text(when (o) { OrdenVod.VALORACION -> "Valoración"; OrdenVod.RECIENTES -> "Recientes"; OrdenVod.TITULO -> "Título" }) })
                }
                BotonSecundario("Exportar películas.m3u") {
                    scope.launch {
                        mensaje = try {
                            val cuenta = app.repositorio.cuentaEfectiva() ?: error("Falta configurar la cuenta")
                            val texto = MotorVod.m3uPeliculas(titulos, categorias.associate { it.id to it.nombre }, cuenta)
                            abrirIntent(app.exportador.intentCompartir(listOf("peliculas.m3u" to texto)))
                            null
                        } catch (e: Exception) { "Error: ${e.message}" }
                    }
                }
            }
            Text("${titulos.size} títulos únicos de ${d?.peliculas?.count { it.categoriaId in conservadas } ?: 0} películas en ${conservadas.size} categorías", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categorias.take(120).forEach { c ->
                    FilterChip(selected = c.id in conservadas, onClick = {
                        seleccion = if (c.id in conservadas) conservadas - c.id else conservadas + c.id
                    }, label = { Text(c.nombre, maxLines = 1) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(visibles.take(2000), key = { it.clave }) { t ->
                    FilaFoco(onClick = {
                        scope.launch { vod.ocultar(t.mejor); ocultas = vod.ocultas(); mensaje = "Oculta: ${t.mejor.nombre} (se puede deshacer en el historial)" }
                    }) {
                        IconoCanal(t.mejor.icono, 36)
                        Column(Modifier.weight(1f)) {
                            Text(t.mejor.nombre.trim(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("★ ${t.mejor.valoracion ?: "—"} · ${t.copias.size} copia(s)" + (t.anio?.let { " · $it" } ?: "") + " · tocar para ocultar",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            val ss = d?.series.orEmpty().filter { qq.isEmpty() || Normalizacion.clave(it.nombre).contains(qq) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(ss.take(2000), key = { it.seriesId }) { s ->
                    FilaFoco(onClick = {
                        scope.launch {
                            mensaje = try {
                                abrirIntent(app.exportador.intentCompartir(listOf("serie_${s.seriesId}.m3u" to vod.episodiosM3u(s)))); null
                            } catch (e: Exception) { "Error: ${e.message}" }
                        }
                    }) {
                        IconoCanal(s.icono, 36)
                        Column(Modifier.weight(1f)) {
                            Text(s.nombre.trim(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("★ ${s.valoracion ?: "—"} · tocar para exportar sus episodios", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
