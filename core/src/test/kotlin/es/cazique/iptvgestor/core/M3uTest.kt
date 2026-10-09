package es.cazique.iptvgestor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {
    private val cuenta = Cuenta("http://ejemplo.invalid:8080", "jorgeprueba", "clave\"rara")
    private val epg = listOf(
        CanalEpg("La 1 HD", listOf("La 1", "La 1 HD"), "https://i/la1.png", 0),
        CanalEpg("La 2", listOf("La 2"), "https://i/la2.png", 1),
    )
    private val cats = listOf(Categoria("1", "ES| MOVISTAR"), Categoria("2", "ES| VODAFONE"), Categoria("3", "ES| TDT", 2), Categoria("9", "FOR ADULTS", 3))
    private val streams = listOf(
        Stream(1, 1, "#### MOVISTAR ####", "1"),
        Stream(2, 2, "ES: LA 1 ᴴᴰ", "1"),
        Stream(3, 3, "ES: LA 1 ᴿᴬᵂ", "1"),
        Stream(4, 4, "ES: LA 2 \"HD\"", "1"),
        Stream(5, 5, "VO: LA 1 ᴴᴰ", "2"),
        Stream(6, 6, "ES: TELEMADRID", "3", icono = "https://p/tm.png"),
        Stream(7, 7, "XXX 1", "9", adulto = true),
    )

    @Test fun formatoYCabecera() {
        val r = Motor().procesar(streams, cats, epg)
        val (m3u, _) = GeneradorM3u.generar(r.lista, cuenta)
        val l = m3u.lines()
        assertTrue(l[0].startsWith("#EXTM3U url-tvg=\""))
        assertTrue(l[0].contains("x-tvg-url="))
        assertEquals(emptyList<String>(), ValidadorM3u.validar(m3u))
        assertTrue(m3u.contains("tvg-id=\"La 1 HD\" tvg-name=\"LA 1\" tvg-logo=\"https://i/la1.png\" group-title=\"M+ 1\",LA 1"))
        assertTrue(m3u.contains("group-title=\"M+ 2\""))
        assertTrue(m3u.contains("group-title=\"Vodafone 1\""))
        assertTrue(m3u.contains("group-title=\"ES| TDT\",ES: TELEMADRID"))
        assertTrue(m3u.contains("tvg-id=\"\"")) // sin guía, tvg-id vacío
        assertTrue(m3u.contains("/live/jorgeprueba/clave%22rara/3.ts"))
        assertFalse(m3u.contains("####"))
        // RAW (capa 1) antes que HD (capa 2)
        assertTrue(m3u.indexOf("/3.ts") < m3u.indexOf("/2.ts"))
        // grupos en orden: capas de paquetes, resto y extras al final
        val grupos = r.lista.map { it.grupoSalida }.distinct()
        assertEquals(listOf("M+ 1", "M+ 2", "Vodafone 1", "ES| TDT", "FOR ADULTS"), grupos)
    }

    @Test fun validadorDetectaErrores() {
        assertFalse(ValidadorM3u.validar("#EXTM3U\n#EXTINF:-1 tvg-id=\"a\"b\",X\nhttp://x/1.ts\n").isEmpty())
        assertFalse(ValidadorM3u.validar("#EXTM3U\n#EXTINF:-1,X\n").isEmpty())
        assertFalse(ValidadorM3u.validar("#EXTM3U\n#EXTINF:-1,X\n#EXTINF:-1,Y\nhttp://x/1.ts\n").isEmpty())
        assertFalse(ValidadorM3u.validar("hola\n").isEmpty())
        assertTrue(ValidadorM3u.validar("#EXTM3U\n#EXTINF:-1 tvg-id=\"\",X\n#EXTVLCOPT:http-user-agent=VLC\nhttp://x/1.ts\n").isEmpty())
    }

    @Test fun modosSinIdsRepetidos() {
        val r = Motor().procesar(streams, cats, epg)
        for (modo in listOf(ModoExportacion.POR_CAPA, ModoExportacion.POR_PAQUETE)) {
            val archivos = GeneradorM3u.exportar(r.lista, cuenta, modo)
            assertTrue(archivos.size > 1)
            for (a in archivos) {
                assertEquals(emptyList<String>(), ValidadorM3u.validar(a.contenido))
                val ids = Regex("tvg-id=\"([^\"]+)\"").findAll(a.contenido).map { it.groupValues[1] }.toList()
                assertEquals("${a.nombre} repite tvg-id", ids.size, ids.toSet().size)
            }
        }
    }

    @Test fun opcionesNumerarYUserAgentYAdultos() {
        val r = Motor().procesar(streams, cats, epg)
        val (m3u, _) = GeneradorM3u.generar(r.lista, cuenta, OpcionesM3u(numerar = true, userAgent = "VLC/3.0.20", incluirAdultos = false))
        assertTrue(m3u.contains("tvg-chno=\"1\""))
        assertTrue(m3u.contains("#EXTVLCOPT:http-user-agent=VLC/3.0.20"))
        assertFalse(m3u.contains("XXX 1"))
        assertEquals(emptyList<String>(), ValidadorM3u.validar(m3u))
    }

    @Test fun redaccion() {
        val t = "Fallo en http://h:80/live/jorgeprueba/clave%22rara/3.ts y player_api.php?username=jorgeprueba&password=secreta; jorgeprueba"
        val r = Redactor.redactar(t, cuenta)
        assertFalse(r.contains("jorgeprueba")); assertFalse(r.contains("secreta"))
        assertFalse(cuenta.toString().contains("jorgeprueba"))
    }
}
