package es.cazique.iptvgestor.datos

import android.content.Context
import es.cazique.iptvgestor.core.Categoria
import es.cazique.iptvgestor.core.ClienteXtream
import es.cazique.iptvgestor.core.Decision
import es.cazique.iptvgestor.core.Pelicula
import es.cazique.iptvgestor.core.Redactor
import es.cazique.iptvgestor.core.Serie
import es.cazique.iptvgestor.core.TipoDecision
import es.cazique.iptvgestor.core.VodJson
import es.cazique.iptvgestor.core.XtreamJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class DatosVod(
    val categorias: List<Categoria>,
    val peliculas: List<Pelicula>,
    val categoriasSeries: List<Categoria>,
    val series: List<Serie>,
)

/**
 * VOD (Fase 4). Las respuestas de la API se guardan tal cual en archivos privados de la app
 * (no van en la base de datos para no hacer crecer el esquema con 67.000 películas).
 */
class RepositorioVod(private val context: Context, private val repo: Repositorio, private val http: okhttp3.OkHttpClient) {
    private val dir get() = File(context.filesDir, "vod").apply { mkdirs() }
    private val _datos = MutableStateFlow<DatosVod?>(null)
    val datos: StateFlow<DatosVod?> = _datos.asStateFlow()

    suspend fun cargar() = withContext(Dispatchers.IO) {
        fun leer(n: String) = File(dir, n).takeIf { it.exists() }?.readText()
        _datos.value = DatosVod(
            categorias = leer("vod_categorias.json")?.let { XtreamJson.categorias(it) }.orEmpty(),
            peliculas = leer("vod.json")?.let { VodJson.peliculas(it) }.orEmpty(),
            categoriasSeries = leer("series_categorias.json")?.let { XtreamJson.categorias(it) }.orEmpty(),
            series = leer("series.json")?.let { VodJson.series(it) }.orEmpty(),
        )
    }

    suspend fun sincronizar(): String = withContext(Dispatchers.IO) {
        val cuenta = repo.cuentaEfectiva() ?: return@withContext "Falta configurar la cuenta"
        val ua = repo.ajustes.leer(Ajustes.K.USER_AGENT) ?: Repositorio.UA_PREDETERMINADO
        try {
            val c = ClienteXtream(http, cuenta, ua)
            // Se escribe a archivos temporales y se renombra al final: todo o nada.
            val nuevos = mapOf(
                "vod_categorias.json" to c.vodCategorias(), "vod.json" to c.vodStreams(),
                "series_categorias.json" to c.seriesCategorias(), "series.json" to c.series(),
            )
            nuevos.forEach { (n, t) -> File(dir, "$n.tmp").writeText(t) }
            nuevos.keys.forEach { n -> File(dir, "$n.tmp").renameTo(File(dir, n)) }
            cargar()
            val d = _datos.value
            "VOD sincronizado: ${d?.peliculas?.size ?: 0} películas y ${d?.series?.size ?: 0} series"
        } catch (e: Exception) {
            "Error: " + Redactor.redactar(e.message ?: e.javaClass.simpleName, cuenta)
        }
    }

    suspend fun episodiosM3u(serie: Serie): String = withContext(Dispatchers.IO) {
        val cuenta = repo.cuentaEfectiva() ?: error("Falta configurar la cuenta")
        val ua = repo.ajustes.leer(Ajustes.K.USER_AGENT) ?: Repositorio.UA_PREDETERMINADO
        val eps = VodJson.episodios(ClienteXtream(http, cuenta, ua).infoSerie(serie.seriesId))
        es.cazique.iptvgestor.core.MotorVod.m3uSerie(serie, eps, cuenta)
    }

    suspend fun ocultas(): Set<Long> =
        repo.decisiones().filter { it.activa && it.tipo == TipoDecision.OCULTAR_PELICULA }.mapNotNull { it.streamId }.toSet()

    suspend fun ocultar(p: Pelicula) =
        repo.decidir(Decision(tipo = TipoDecision.OCULTAR_PELICULA, streamId = p.streamId, nombre = p.nombre, clave = p.tmdb.ifBlank { p.nombre }))
}
