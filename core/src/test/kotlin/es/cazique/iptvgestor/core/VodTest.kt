package es.cazique.iptvgestor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 4 con datos simulados (no hay vod.json en los fixtures). */
class VodTest {
    private val json = """[
      {"num":1,"name":"ES - El Gran Viaje (2026)","stream_type":"movie","stream_id":11,"stream_icon":"i1","rating":"7.1","tmdb":"555","added":"100","is_adult":0,"category_id":"5","container_extension":"mkv"},
      {"num":2,"name":"ES - El gran viaje 4K (2026)","stream_id":12,"rating":"7.4","tmdb":"555","added":"200","is_adult":0,"category_id":"6","container_extension":"mp4"},
      {"num":3,"name":"ES - Sin Tmdb (2020)","stream_id":13,"rating":"5","tmdb":"","added":"50","is_adult":0,"category_id":"5"},
      {"num":4,"name":"ES - Sin TMDB [HD] (2020)","stream_id":14,"rating":"6","tmdb":null,"added":"60","is_adult":"0","category_id":"5"},
      {"num":5,"name":"XXX - Adultos","stream_id":15,"rating":"","tmdb":"","added":"10","is_adult":"1","category_id":"9"},
      {"num":6,"name":"EN - Other Film (2019)","stream_id":16,"rating":"9","tmdb":"777","added":"300","is_adult":0,"category_id":"7"}
    ]"""

    @Test fun duplicadosOrdenYFiltro() {
        val ps = VodJson.peliculas(json)
        assertEquals(6, ps.size)
        val t = MotorVod.procesar(ps, setOf("5", "6", "9"))
        assertEquals(2, t.size)                         // 555 unida, "Sin Tmdb" unida por título y año, adultos fuera
        assertEquals(12L, t.first().mejor.streamId)     // mejor valoración primero
        assertEquals(2, t.first { it.clave == "tmdb:555" }.copias.size)
        assertEquals(2, t.first { it.clave.startsWith("t:") }.copias.size)
        assertEquals("ES", MotorVod.prefijoIdioma("ES - Título (2026)"))
        assertEquals("TITULO" to "2026", MotorVod.claveTitulo("ES - Título (2026)"))
        val recientes = MotorVod.procesar(ps, null, orden = OrdenVod.RECIENTES)
        assertEquals(16L, recientes.first().mejor.streamId)
        assertTrue(MotorVod.procesar(ps, null, incluirAdultos = true).any { it.mejor.adulto })
    }

    @Test fun m3uPeliculasYSeries() {
        val c = Cuenta("http://{HOST}", "{USER}", "{PASS}")
        val t = MotorVod.procesar(VodJson.peliculas(json), setOf("5", "6"))
        val m3u = MotorVod.m3uPeliculas(t, mapOf("5" to "Cine ES", "6" to "4K"), c)
        assertEquals(emptyList<String>(), ValidadorM3u.validar(m3u))
        assertTrue(m3u.contains("/movie/%7BUSER%7D/%7BPASS%7D/12.mp4"))
        val info = """{"info":{"name":"Serie"},"episodes":{"1":[{"id":"901","episode_num":2,"title":"B","container_extension":"mkv","season":1},
            {"id":"900","episode_num":1,"title":"A","container_extension":"mkv","season":1}],"2":[{"id":"950","episode_num":1,"title":"C","season":2}]}}"""
        val eps = VodJson.episodios(info)
        assertEquals(listOf(900L, 901L, 950L), eps.map { it.id })
        val s = VodJson.series("""[{"series_id":"77","name":"ES - Serie","category_id":"3","cover":"c","rating":"8"}]""").first()
        val m = MotorVod.m3uSerie(s, eps, c)
        assertEquals(emptyList<String>(), ValidadorM3u.validar(m))
        assertTrue(m.contains("T01E01 - A"))
        assertTrue(m.contains("/series/%7BUSER%7D/%7BPASS%7D/900.mkv"))
    }
}
