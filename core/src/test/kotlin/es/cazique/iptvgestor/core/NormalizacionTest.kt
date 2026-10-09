package es.cazique.iptvgestor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizacionTest {
    @Test fun superindicesYPrefijos() {
        listOf("ES: MOVISTAR LALIGA ᴴᴰ", "ES: MOVISTAR LALIGA SD", "ES: MOVISTAR LALIGA ᴿᴬᵂ",
            "ES: MOVISTAR LALIGA ʰᵉᵛᶜ", "ES: MOVISTAR LALIGA ᵁᴸᵀᴿᴬ ᴿᴬᵂ", "M+: M.LALIGA ᴿᴬᵂ").forEach {
            assertEquals(it, "M+ LALIGA", Normalizacion.clave(it))
        }
    }

    @Test fun numerosDistinguenCanales() {
        assertEquals("M+ LALIGA 2", Normalizacion.clave("ES: MOVISTAR LALIGA 2 ᴴᴰ"))
        assertNotEquals(Normalizacion.clave("MOVISTAR LALIGA 2"), Normalizacion.clave("MOVISTAR LALIGA 3"))
    }

    @Test fun parentesisYOperadoras() {
        assertEquals("M+ LALIGA", Normalizacion.clave("ES: MOVISTAR LALIGA ᴴᴰᴿ ( SOLO EVENTOS)"))
        assertEquals("LA 1", Normalizacion.clave("VO: LA 1 ᴴᴰ"))
        assertEquals("LA 1", Normalizacion.clave("GO: LA 1 ᴿᴬᵂ"))
        assertEquals("LA 1", Normalizacion.clave("M+: LA 1 ᴿᴬᵂ"))
        assertEquals("M+ LA 1", Normalizacion.clave("MOVISTAR+ LA 1"))
        assertEquals("M+ ESTRENOS", Normalizacion.clave("MOVISTAR PLUS+ ESTRENOS"))
    }

    @Test fun calidades() {
        val p = Normalizacion.PERFIL_NORMAL
        assertEquals(0, Normalizacion.puntos("LA 1 ᴿᴬᵂ", p))
        assertEquals(3, Normalizacion.puntos("LA 1", p)) // sin etiqueta = HD
        assertEquals(6, Normalizacion.puntos("LA 1 SD ᴸᴼᵂ", p))
        assertTrue(Normalizacion.puntos("LALIGA ᴴᴰᴿ (SOLO EVENTOS)", p) >= 10)
        assertEquals(0, Normalizacion.puntos("LA 1 HD", Normalizacion.PERFIL_LIGERO))
    }

    @Test fun respaldo() {
        assertTrue(Normalizacion.esRespaldo("ES: M+ LIGA DE CAMPEONES ᴿᴬᵂ ⁽ᴮᴷ⁾", "ES| X"))
        assertTrue(Normalizacion.esRespaldo("LA 1", "ES| MOVISTAR BK"))
    }

    @Test fun difflibComoPython() {
        // Valores calculados con difflib.SequenceMatcher(None, a, b).ratio() de Python 3.
        assertEquals(0.6, Difflib.ratio("ABCDE", "ABCXY"), 1e-9)
        assertEquals(0.75, Difflib.ratio("abcd", "bcde"), 1e-9)
        assertEquals(0.9333333333333333, Difflib.ratio("M+LALIGA", "MLALIGA"), 1e-9)
        assertEquals(2.0 * 4 / 10, Difflib.ratio("ABCDE", "ABCDX"), 1e-9)
        assertEquals(1.0, Difflib.ratio("LALIGA", "LALIGA"), 1e-9)
    }
}
