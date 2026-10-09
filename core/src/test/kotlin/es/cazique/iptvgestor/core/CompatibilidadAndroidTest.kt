package es.cazique.iptvgestor.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El motor de expresiones regulares de Android (ICU) no admite algunos modificadores en línea de Java,
 * como `(?U)`: compilaban en la JVM pero cerraban la app en Android 16. Esta prueba lo impide.
 */
class CompatibilidadAndroidTest {
    @Test fun sinModificadoresQueIcuNoAdmite() {
        val fuentes = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(fuentes.isNotEmpty())
        val prohibidos = listOf("(?U)", "(?u)", "(?x)", "(?d)")
        val malos = fuentes.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l ->
                if (prohibidos.any { "\"$it" in l || "\"^$it" in l }) "${f.name}:${i + 1}: ${l.trim()}" else null
            }
        }
        assertTrue("Modificadores no admitidos por Android:\n" + malos.joinToString("\n"), malos.isEmpty())
    }

    @Test fun soloReUActivaElIndicadorYNuncaEnAndroid() {
        // UNICODE_CHARACTER_CLASS lanza IllegalArgumentException en Android: solo puede aparecer en reU, tras comprobar EN_ANDROID.
        val usos = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().filter { "UNICODE_CHARACTER_CLASS)" in it }.map { f.name to it.trim() } }.toList()
        assertTrue(usos.toString(), usos.size == 1 && usos[0].first == "Normalizacion.kt" && "EN_ANDROID" in usos[0].second)
        assertTrue(!EN_ANDROID) // en la JVM de las pruebas
    }

    @Test fun normalizacionSigueSiendoUnicode() {
        // Con UNICODE_CHARACTER_CLASS, \w incluye letras no ASCII (en Android, ICU ya lo hace siempre).
        assertTrue(Normalizacion.clave("ES: CANAL ÑANDÚ ᴴᴰ") == "CANAL NANDU")
        assertTrue(Normalizacion.clave("ES: ТЕЛЕ 5") == "ТЕЛЕ 5")
    }
}
