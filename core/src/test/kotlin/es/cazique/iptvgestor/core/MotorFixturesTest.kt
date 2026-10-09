package es.cazique.iptvgestor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cifras de referencia de la sección 11.2 de SPEC.md con los fixtures entregados. */
class MotorFixturesTest {
    private val streams = Datos.streams
    private val cats = Datos.categorias
    private val nombre = cats.associate { it.id to it.nombre }

    @Test fun origen() {
        assertEquals(24401, streams.size)
        assertEquals(24401, streams.map { it.streamId }.toSet().size)
        assertEquals(313, cats.size)
    }

    @Test fun filtro() {
        val es = cats.filter { it.nombre.startsWith("ES|") }
        assertEquals(56, es.size)
        assertEquals(2737, streams.count { nombre[it.categoriaId]?.startsWith("ES|") == true })
        val cfg = ConfigMotor()
        assertEquals(57, cats.count { cfg.conserva(it.nombre) })
        assertEquals(2907, streams.count { nombre[it.categoriaId]?.let(cfg::conserva) == true })
        val ppv = cats.filter { "PPV" in it.nombre && !it.nombre.startsWith("ES|") }.map { it.nombre }.toSet()
        assertEquals(119, ppv.size)
        assertEquals(8281, streams.count { nombre[it.categoriaId] in ppv })
    }

    /** 570 nombres / 1.173 entradas: nombres iguales sin distinguir mayúsculas y con los espacios colapsados (ver DECISIONES.md). */
    @Test fun nombresRepetidos() {
        val n = streams.groupingBy { it.nombre.trim().split(Regex("\\s+")).joinToString(" ").lowercase() }.eachCount().filterValues { it > 1 }
        assertEquals(570, n.size)
        assertEquals(1173, n.values.sum())
    }

    @Test fun paquetesYCapas() {
        val r = Motor().procesar(streams, cats, emptyList())
        val p = r.cifras.paquetes.associateBy { it.nombre }
        assertEquals(201, p.getValue("M+").canales); assertEquals(388, p.getValue("M+").entradas)
        assertEquals(listOf(201, 80, 57, 29, 15), p.getValue("M+").capas)
        assertEquals(140, p.getValue("Vodafone").canales); assertEquals(189, p.getValue("Vodafone").entradas)
        assertEquals(listOf(140, 48, 1), p.getValue("Vodafone").capas)
        assertEquals(146, p.getValue("Orange").canales); assertEquals(148, p.getValue("Orange").entradas)
        assertEquals(listOf(146, 2), p.getValue("Orange").capas)
        assertEquals(719, r.lista.count { it.canal.tipoAmbito == TipoAmbito.PAQUETE })
        val laliga = r.canales.first { it.ambito == "M+" && it.clave == "M+ LALIGA" }
        assertEquals(8, laliga.variantes.size) // 7 del grupo VIP + 1 de MOVISTAR SPORT
        assertEquals(5, r.lista.count { it.canal === laliga })
        assertTrue(laliga.variantes.last().puntos >= 10) // SOLO EVENTOS al final
    }

    @Test fun respaldoDespuesAIgualCalidad() {
        val r = Motor().procesar(streams, cats, emptyList())
        val c = r.canales.first { it.ambito == "M+" && it.clave == "M+ LIGA DE CAMPEONES" }
        val raw = c.variantes.filter { it.puntos == 0 }
        assertEquals(3, raw.size)
        assertTrue(raw.last().respaldo)
    }

    @Test fun guia() {
        val epg = Datos.epgObligatoria()
        assertEquals(640, epg.size)
        assertEquals(639, epg.count { it.icono.isNotEmpty() })
        val r = Motor().procesar(streams, cats, epg)
        val p = r.cifras.paquetes.associateBy { it.nombre }
        assertEquals(136, p.getValue("M+").conGuia)
        assertEquals(87, p.getValue("Vodafone").conGuia)
        assertEquals(109, p.getValue("Orange").conGuia)
        // La lista exportada es válida y las capas siguen el orden de la guía.
        val m3u = GeneradorM3u.generar(r.lista, Cuenta("http://{HOST}", "{USER}", "{PASS}")).first
        assertEquals(emptyList<String>(), ValidadorM3u.validar(m3u))
        val capa1 = r.lista.filter { it.grupoSalida == "M+ 1" && it.canal.emparejado != null }.map { it.canal.emparejado!!.canal.posicion }
        assertEquals(capa1.sorted(), capa1)
        println("Cifras de la lista final: ${r.cifras}")
    }

    @Test fun listaFinalSinDuplicarGrupos() {
        val r = Motor().procesar(streams, cats, emptyList())
        val grupos = r.lista.map { it.grupoSalida }.distinct()
        assertEquals(listOf("M+ 1", "M+ 2", "M+ 3", "M+ 4", "M+ 5", "Vodafone 1", "Vodafone 2", "Vodafone 3", "Orange 1", "Orange 2"), grupos.take(10))
        assertEquals("FOR ADULTS", grupos.last())
        val cfg = ConfigMotor()
        assertTrue(grupos.drop(10).none { g -> cfg.paqueteDe(g) != null })
        // Cada stream sale como mucho una vez.
        assertEquals(r.lista.size, r.lista.map { it.variante.stream.streamId }.toSet().size)
        // Sin separadores.
        assertTrue(r.lista.none { Normalizacion.esSeparador(it.variante.stream.nombre) })
    }
}
