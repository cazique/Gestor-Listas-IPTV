package es.cazique.iptvgestor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionesTest {
    private val epg = listOf(
        CanalEpg("La 1 HD", listOf("La 1"), "https://i/la1.png", 0),
        CanalEpg("Cuatro HD", listOf("Cuatro"), "https://i/4.png", 1),
        CanalEpg("Teledeporte HD", listOf("Teledeporte"), "https://i/tdp.png", 2),
    )
    private val cats = listOf(Categoria("1", "ES| MOVISTAR"), Categoria("3", "ES| TDT", 1))
    private val base = listOf(
        Stream(10, 1, "ES: LA 1 ᴴᴰ", "1"),
        Stream(11, 2, "ES: LA 1 ᴿᴬᵂ", "1"),
        Stream(12, 3, "ES: TVE 1 ᴴᴰ", "1"),
        Stream(13, 4, "ES: TDP ᴴᴰ", "1"),
        Stream(14, 5, "ES: CUATRO", "3"),
    )
    private fun variante(s: Stream, tipo: TipoDecision, valor: String = "", ambito: String = "", fecha: Long = 1) =
        Decision(tipo = tipo, streamId = s.streamId, nombre = Normalizacion.nombreNormalizado(s.nombre), clave = Normalizacion.clave(s.nombre),
            grupo = cats.first { it.id == s.categoriaId }.nombre, valor = valor, ambito = ambito, fecha = fecha)

    @Test fun unirSepararPreferidaYOcultar() {
        val d = listOf(
            variante(base[2], TipoDecision.UNIR, "LA 1"),
            variante(base[0], TipoDecision.PREFERIDA),
            variante(base[1], TipoDecision.OCULTAR_VARIANTE),
        )
        val r = Motor().procesar(base, cats, epg, d)
        val la1 = r.canales.first { it.clave == "LA 1" && it.ambito == "M+" }
        assertEquals(3, la1.variantes.size)
        assertEquals(10L, la1.variantes.first().stream.streamId) // preferida en capa 1
        assertTrue(r.lista.none { it.variante.stream.streamId == 11L })
        val sep = Motor().procesar(base, cats, epg, listOf(variante(base[1], TipoDecision.SEPARAR)))
        assertEquals(1, sep.canales.first { it.clave == "LA 1" && it.ambito == "M+" }.variantes.size)
    }

    @Test fun guiaManualYQuitar() {
        val asignar = Decision(tipo = TipoDecision.ASIGNAR_EPG, clave = "TDP", valor = "Teledeporte HD", fecha = 1)
        val r = Motor().procesar(base, cats, epg, listOf(asignar))
        assertEquals("Teledeporte HD", r.canales.first { it.clave == "TDP" }.tvgId)
        val quitar = Decision(tipo = TipoDecision.QUITAR_EPG, clave = "LA 1", fecha = 2)
        val r2 = Motor().procesar(base, cats, epg, listOf(asignar, quitar))
        assertNull(r2.canales.first { it.clave == "LA 1" }.emparejado)
        // Deshacer (activa = false) devuelve el emparejado automático.
        val r3 = Motor().procesar(base, cats, epg, listOf(asignar, quitar.copy(activa = false)))
        assertEquals("La 1 HD", r3.canales.first { it.clave == "LA 1" }.tvgId)
    }

    @Test fun cambiarPaqueteEIcono() {
        val d = listOf(
            variante(base[4], TipoDecision.CAMBIAR_PAQUETE, "M+"),
            Decision(tipo = TipoDecision.ICONO, clave = "CUATRO", valor = "https://mio/4.png"),
        )
        val r = Motor().procesar(base, cats, epg, d)
        val c = r.canales.first { it.clave == "CUATRO" }
        assertEquals("M+", c.ambito)
        assertEquals("https://mio/4.png", c.icono)
    }

    @Test fun sobreviveACambioDeStreamId() {
        val d = listOf(variante(base[1], TipoDecision.OCULTAR_VARIANTE))
        val nuevos = base.map { if (it.streamId == 11L) it.copy(streamId = 999) else it }
        val r = Motor().procesar(nuevos, cats, epg, d)
        assertTrue(r.lista.none { it.variante.stream.streamId == 999L })
        assertTrue(r.decisionesHuerfanas.isEmpty())
        val sinCanal = Motor().procesar(base.filter { it.streamId != 11L }, cats, epg, d)
        assertEquals(1, sinCanal.decisionesHuerfanas.size)
    }

    @Test fun exportarEImportar() {
        val d = listOf(variante(base[0], TipoDecision.PREFERIDA), Decision(tipo = TipoDecision.ASIGNAR_EPG, clave = "TDP", valor = "Teledeporte HD"))
        val texto = Decisiones.exportar(d)
        assertEquals(d, Decisiones.importar(texto))
        val simple = Decisiones.importar("# alias\nTDP = Teledeporte\n", Emparejador(epg))
        assertEquals(1, simple.size)
        assertEquals("TDP", simple[0].clave)
        assertEquals("Teledeporte HD", simple[0].valor)
    }

    @Test fun bandejaDeRevision() {
        val epg2 = epg + CanalEpg("LaLiga TV", listOf("LaLiga TV"), "", 3)
        val streams = base + Stream(20, 9, "ES: LALIGA TV ᴴᴰ", "3") + Stream(21, 10, "ES: XYZ RARO", "3")
        val r = Motor().procesar(streams, cats, epg2)
        val casos = BandejaRevision.casos(r, emptyList())
        assertTrue(casos.any { it.tipo == TipoCaso.SIN_GUIA && it.clave == "XYZ RARO" })
        val ign = Decision(tipo = TipoDecision.IGNORAR, clave = "XYZ RARO", valor = TipoCaso.SIN_GUIA.name)
        assertTrue(BandejaRevision.casos(r, listOf(ign)).none { it.tipo == TipoCaso.SIN_GUIA && it.clave == "XYZ RARO" })
    }

    @Test fun ordenarPorTasaMedida() {
        val r = Motor(ConfigMotor(ordenarPorTasa = true)).procesar(base, cats, epg, tasas = mapOf(10L to 8000L, 11L to 3000L))
        assertEquals(10L, r.canales.first { it.clave == "LA 1" && it.ambito == "M+" }.variantes.first().stream.streamId)
        val r2 = Motor().procesar(base, cats, epg, tasas = mapOf(10L to 8000L, 11L to 3000L))
        assertEquals(11L, r2.canales.first { it.clave == "LA 1" && it.ambito == "M+" }.variantes.first().stream.streamId) // RAW por etiqueta
    }

    @Test fun cambios() {
        val a = listOf(Instantanea(1, "LA 1", "G", "i"), Instantanea(2, "LA 2", "G", "i"), Instantanea(3, "TRES", "G", "i"))
        val d = listOf(Instantanea(1, "LA 1 HD", "G", "i"), Instantanea(30, "TRES", "G", "j"), Instantanea(4, "NUEVO", "G", "i"))
        val inf = Comparador.comparar(a, d, 0)
        assertEquals(listOf(4L), inf.anadidos.map { it.streamId })
        assertEquals(listOf(2L), inf.quitados.map { it.streamId })
        assertEquals(2, inf.cambiados.size)
        assertTrue(inf.cambiados.any { "stream_id" in it.que })
        assertTrue(inf.comoTexto().contains("Añadidos: 1"))
    }
}
