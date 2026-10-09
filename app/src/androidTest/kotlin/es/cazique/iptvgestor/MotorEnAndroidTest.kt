package es.cazique.iptvgestor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.cazique.iptvgestor.core.ConfigMotor
import es.cazique.iptvgestor.core.Cuenta
import es.cazique.iptvgestor.core.GeneradorM3u
import es.cazique.iptvgestor.core.Motor
import es.cazique.iptvgestor.core.Normalizacion
import es.cazique.iptvgestor.core.ValidadorM3u
import es.cazique.iptvgestor.core.XtreamJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El mismo motor, ejecutado en Android (expresiones regulares de ICU, no de la JVM), debe dar las cifras
 * de referencia de la sección 11.2 con los fixtures. CI copia fixtures/ a los assets de esta prueba.
 */
@RunWith(AndroidJUnit4::class)
class MotorEnAndroidTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test fun normalizacionIgualQueEnLaJvm() {
        assertEquals("M+ LALIGA", Normalizacion.clave("ES: MOVISTAR LALIGA ᵁᴸᵀᴿᴬ ᴿᴬᵂ"))
        assertEquals("M+ LALIGA 2", Normalizacion.clave("ES: MOVISTAR LALIGA 2 ᴴᴰ"))
        assertEquals("CANAL NANDU", Normalizacion.clave("ES: CANAL ÑANDÚ ᴴᴰ"))
        assertEquals(0, Normalizacion.puntos("LA 1 ᴿᴬᵂ"))
        assertEquals(6, Normalizacion.puntos("LA 1 SD ᴸᴼᵂ"))
    }

    @Test fun cifrasDeReferenciaConLosFixtures() {
        val cats = XtreamJson.categorias(assets.open("live_categorias.json").bufferedReader().use { it.readText() })
        val streams = assets.open("live.json").use { XtreamJson.streams(it) }
        assertEquals(24401, streams.size)
        assertEquals(313, cats.size)
        val r = Motor(ConfigMotor()).procesar(streams, cats, emptyList())
        val p = r.cifras.paquetes.associateBy { it.nombre }
        assertEquals(listOf(201, 80, 57, 29, 15), p.getValue("M+").capas)
        assertEquals(388, p.getValue("M+").entradas)
        assertEquals(listOf(140, 48, 1), p.getValue("Vodafone").capas)
        assertEquals(listOf(146, 2), p.getValue("Orange").capas)
        assertEquals(2907, r.cifras.entradasConservadas)
        assertEquals(2816, r.cifras.entradasLista)
        val m3u = GeneradorM3u.generar(r.lista, Cuenta("http://{HOST}", "{USER}", "{PASS}")).first
        assertEquals(emptyList<String>(), ValidadorM3u.validar(m3u))
    }
}
