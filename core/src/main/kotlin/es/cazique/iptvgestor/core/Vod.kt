package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Película de `get_vod_streams` (Fase 4). */
@Serializable
data class Pelicula(
    val streamId: Long,
    val nombre: String,
    val categoriaId: String,
    val icono: String = "",
    val valoracion: Double? = null,
    val tmdb: String = "",
    val anadido: Long = 0,
    val adulto: Boolean = false,
    val extension: String = "mp4",
)

/** Serie de `get_series` y sus episodios de `get_series_info`. */
@Serializable
data class Serie(val seriesId: Long, val nombre: String, val categoriaId: String, val icono: String = "", val valoracion: Double? = null, val tmdb: String = "")

@Serializable
data class Episodio(val id: Long, val temporada: Int, val numero: Int, val titulo: String, val extension: String = "mp4")

object VodJson {
    private fun lista(el: JsonElement): List<JsonElement> = when (el) {
        is JsonArray -> el
        is JsonObject -> el.values.toList()
        else -> emptyList()
    }

    fun peliculas(texto: String): List<Pelicula> = with(XtreamJson) {
        lista(json.parseToJsonElement(texto)).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Pelicula(
                streamId = o.texto("stream_id")?.toLongOrNull() ?: return@mapNotNull null,
                nombre = o.texto("name") ?: "",
                categoriaId = o.texto("category_id") ?: "",
                icono = o.texto("stream_icon") ?: "",
                valoracion = o.texto("rating")?.replace(',', '.')?.toDoubleOrNull(),
                tmdb = o.texto("tmdb")?.trim().orEmpty().let { if (it == "0" || it.equals("null", true)) "" else it },
                anadido = o.texto("added")?.toLongOrNull() ?: 0,
                adulto = o.texto("is_adult") == "1",
                extension = o.texto("container_extension")?.ifBlank { null } ?: "mp4",
            )
        }
    }

    fun series(texto: String): List<Serie> = with(XtreamJson) {
        lista(json.parseToJsonElement(texto)).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Serie(
                seriesId = o.texto("series_id")?.toLongOrNull() ?: return@mapNotNull null,
                nombre = o.texto("name") ?: "",
                categoriaId = o.texto("category_id") ?: "",
                icono = o.texto("cover") ?: "",
                valoracion = o.texto("rating")?.replace(',', '.')?.toDoubleOrNull(),
                tmdb = o.texto("tmdb")?.trim().orEmpty(),
            )
        }
    }

    /** `episodes` es un objeto { "1": [ ... ], "2": [ ... ] } con los episodios de cada temporada. */
    fun episodios(texto: String): List<Episodio> = with(XtreamJson) {
        val raiz = json.parseToJsonElement(texto).jsonObject
        val eps = raiz["episodes"] ?: return emptyList()
        val porTemporada: List<Pair<String, JsonElement>> = when (eps) {
            is JsonObject -> eps.entries.map { it.key to it.value }
            is JsonArray -> eps.mapIndexed { i, e -> "${i + 1}" to e }
            else -> emptyList()
        }
        porTemporada.flatMap { (t, l) ->
            lista(l).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                Episodio(
                    id = o.texto("id")?.toLongOrNull() ?: return@mapNotNull null,
                    temporada = o.texto("season")?.toIntOrNull() ?: t.toIntOrNull() ?: 0,
                    numero = o.texto("episode_num")?.toIntOrNull() ?: 0,
                    titulo = o.texto("title") ?: "",
                    extension = o.texto("container_extension")?.ifBlank { null } ?: "mp4",
                )
            }
        }.sortedWith(compareBy({ it.temporada }, { it.numero }))
    }
}

enum class OrdenVod { VALORACION, RECIENTES, TITULO }

data class TituloVod(val clave: String, val titulo: String, val anio: String?, val copias: List<Pelicula>) {
    val mejor: Pelicula get() = copias.first()
}

/**
 * Limpieza de películas: filtro por categorías, duplicados (por `tmdb` y, si no hay, por título normalizado
 * y año), ocultar adultos y ordenar por valoración o fecha de alta.
 */
object MotorVod {
    private val PREFIJO = Regex("^\\s*[A-Z]{2,3}\\s*[-|:]\\s*")
    private val ANIO = Regex("\\((19|20)\\d{2}\\)|\\b(19|20)\\d{2}\\b")

    /** "ES - Título (2026)" → ("TITULO", "2026"). */
    fun claveTitulo(nombre: String): Pair<String, String?> {
        val limpio = Normalizacion.limpio(nombre)
        val anio = ANIO.findAll(limpio).lastOrNull()?.value?.trim('(', ')')
        var t = PREFIJO.replaceFirst(limpio, "")
        t = ANIO.replace(t, " ")
        t = Regex("\\[.*?]|\\(.*?\\)").replace(t, " ")
        t = Regex("(?U)\\b(4K|UHD|FHD|HD|SD|HEVC|H265|VOSE|VOS|DUAL|LAT|CAST|ESP|MULTI)\\b").replace(t, " ")
        t = Regex("(?U)[^\\w ]").replace(t, " ")
        return Regex("\\s+").replace(t, " ").trim() to anio
    }

    fun prefijoIdioma(nombre: String): String? = PREFIJO.find(Normalizacion.limpio(nombre))?.value?.trim()?.trimEnd('-', '|', ':')?.trim()

    fun procesar(
        peliculas: List<Pelicula>,
        categoriasConservadas: Set<String>?,
        incluirAdultos: Boolean = false,
        orden: OrdenVod = OrdenVod.VALORACION,
        ocultas: Set<Long> = emptySet(),
    ): List<TituloVod> {
        val filtradas = peliculas.filter {
            (categoriasConservadas == null || it.categoriaId in categoriasConservadas) && (incluirAdultos || !it.adulto) && it.streamId !in ocultas
        }
        val grupos = LinkedHashMap<String, MutableList<Pelicula>>()
        for (p in filtradas) {
            val (t, anio) = claveTitulo(p.nombre)
            val clave = if (p.tmdb.isNotBlank()) "tmdb:${p.tmdb}" else "t:$t|${anio ?: ""}"
            grupos.getOrPut(clave) { ArrayList() }.add(p)
        }
        val titulos = grupos.map { (k, l) ->
            val copias = l.sortedWith(compareByDescending<Pelicula> { it.valoracion ?: -1.0 }.thenByDescending { it.anadido })
            val (t, anio) = claveTitulo(copias.first().nombre)
            TituloVod(k, t, anio, copias)
        }
        return when (orden) {
            OrdenVod.VALORACION -> titulos.sortedWith(compareByDescending<TituloVod> { it.mejor.valoracion ?: -1.0 }.thenBy { it.titulo })
            OrdenVod.RECIENTES -> titulos.sortedByDescending { t -> t.copias.maxOf { it.anadido } }
            OrdenVod.TITULO -> titulos.sortedBy { it.titulo }
        }
    }

    fun urlPelicula(c: Cuenta, p: Pelicula): String =
        "${c.base}/movie/${java.net.URLEncoder.encode(c.usuario, "UTF-8")}/${java.net.URLEncoder.encode(c.contrasena, "UTF-8")}/${p.streamId}.${p.extension}"

    fun urlEpisodio(c: Cuenta, e: Episodio): String =
        "${c.base}/series/${java.net.URLEncoder.encode(c.usuario, "UTF-8")}/${java.net.URLEncoder.encode(c.contrasena, "UTF-8")}/${e.id}.${e.extension}"

    /** M3U de películas independiente del de directos: una entrada por título (la mejor copia). */
    fun m3uPeliculas(titulos: List<TituloVod>, categorias: Map<String, String>, cuenta: Cuenta): String = buildString {
        append("#EXTM3U\n")
        for (t in titulos) {
            val p = t.mejor
            val nombre = GeneradorM3u.titulo(p.nombre.trim())
            append("#EXTINF:-1 tvg-id=\"\" tvg-name=\"").append(GeneradorM3u.atributo(nombre))
                .append("\" tvg-logo=\"").append(GeneradorM3u.atributo(p.icono))
                .append("\" group-title=\"").append(GeneradorM3u.atributo(categorias[p.categoriaId] ?: "Películas")).append("\",")
                .append(nombre).append('\n')
            append(urlPelicula(cuenta, p)).append('\n')
        }
    }

    fun m3uSerie(serie: Serie, episodios: List<Episodio>, cuenta: Cuenta): String = buildString {
        append("#EXTM3U\n")
        for (e in episodios) {
            val nombre = GeneradorM3u.titulo("${serie.nombre.trim()} T%02dE%02d".format(e.temporada, e.numero) + if (e.titulo.isNotBlank()) " - ${e.titulo}" else "")
            append("#EXTINF:-1 tvg-id=\"\" tvg-name=\"").append(GeneradorM3u.atributo(nombre))
                .append("\" tvg-logo=\"").append(GeneradorM3u.atributo(serie.icono))
                .append("\" group-title=\"").append(GeneradorM3u.atributo(serie.nombre.trim())).append("\",").append(nombre).append('\n')
            append(urlEpisodio(cuenta, e)).append('\n')
        }
    }
}
