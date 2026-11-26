package es.cazique.iptvgestor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import es.cazique.iptvgestor.core.XtreamJson
import es.cazique.iptvgestor.ui.AppRaiz
import es.cazique.iptvgestor.ui.TemaApp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de todas las pantallas con los fixtures cargados (24.401 canales) y, si hay red, la guía de dobleM.
 * Se guardan en app/build/capturas y GitHub Actions las adjunta al Release. No son una prueba de corrección:
 * si una pantalla no se puede capturar, se anota y se sigue.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturasTest {
    @get:Rule val compose = createComposeRule()
    private val dir = File("build/capturas").apply { mkdirs() }

    private fun cargar(): IptvGestorApp {
        val app = ApplicationProvider.getApplicationContext<IptvGestorApp>()
        val fx = File("../fixtures")
        runBlocking {
            app.repositorio.guardarProveedor(
                XtreamJson.categorias(File(fx, "live_categorias.json").readText()),
                XtreamJson.streams(File(fx, "live.json").readText()),
            )
            runCatching { app.repositorio.actualizarEpg() }
        }
        return app
    }

    private fun foto(nombre: String) {
        compose.waitForIdle()
        runCatching {
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "captura-app-$nombre.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("No se pudo capturar $nombre: $it") }
    }

    private fun paso(nombre: String, accion: () -> Unit) {
        runCatching { accion(); foto(nombre) }.onFailure { println("Paso $nombre omitido: $it") }
    }

    private fun recorrer(prefijo: String, tv: Boolean) {
        val app = cargar()
        compose.setContent { TemaApp(tv) { AppRaiz(app, tv, buscarActualizaciones = false, comprobarAlAbrir = false) {} } }
        paso("$prefijo-1-resumen") { }
        paso("$prefijo-2-lista") { compose.onNodeWithTag("nav_LISTA").performClick() }
        paso("$prefijo-3-ficha") {
            compose.onNodeWithTag("lista").performScrollToNode(hasText("M+ LALIGA"))
            compose.onNodeWithText("M+ LALIGA").performClick()
        }
        paso("$prefijo-4-revisar") { compose.onNodeWithTag("nav_REVISAR").performClick() }
        paso("$prefijo-5-exportar") { compose.onNodeWithTag("nav_EXPORTAR").performClick() }
        paso("$prefijo-6-ajustes") { compose.onNodeWithTag("nav_AJUSTES").performClick() }
        paso("$prefijo-7-utilidades") {
            compose.onNodeWithText("Servidor local, subida, comprobar enlaces y bloqueo parental →").performScrollTo().performClick()
        }
        paso("$prefijo-8-registro") {
            compose.onNodeWithTag("nav_AJUSTES").performClick()
            compose.onNodeWithText("Registro de actividad (diagnóstico) →").performScrollTo().performClick()
        }
        paso("$prefijo-9-version") {
            compose.onNodeWithTag("nav_AJUSTES").performClick()
            compose.onNodeWithText("Versión y actualizaciones").performScrollTo().performClick()
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h1100dp-port-xhdpi")
    fun movil() = recorrer("movil", tv = false)

    @Test @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun tv() = recorrer("tv", tv = true)
}
