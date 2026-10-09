package es.cazique.iptvgestor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import es.cazique.iptvgestor.core.Categoria
import es.cazique.iptvgestor.core.Stream
import es.cazique.iptvgestor.ui.AppRaiz
import es.cazique.iptvgestor.ui.TemaApp
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pruebas de interfaz (sección 11.1): táctil en móvil y D-pad en TV; vista previa, ficha y bandeja. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InterfazTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: IptvGestorApp

    @Before fun datos() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            app.repositorio.guardarProveedor(
                listOf(Categoria("1", "ES| MOVISTAR", 0), Categoria("2", "ES| TDT", 1)),
                listOf(
                    Stream(1, 1, "ES: LA 1 ᴴᴰ", "1"), Stream(2, 2, "ES: LA 1 ᴿᴬᵂ", "1"),
                    Stream(3, 3, "ES: CANAL INVENTADO ᴴᴰ", "1"), Stream(4, 4, "ES: TELEMADRID", "2"),
                ),
            )
        }
    }

    private fun lanzar(tv: Boolean) = compose.setContent { TemaApp(tv) { AppRaiz(app, tv, buscarActualizaciones = false, comprobarAlAbrir = false) {} } }

    @Test fun movilVistaPreviaYFicha() {
        lanzar(tv = false)
        compose.onNodeWithTag("nav_LISTA").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Vista previa", substring = true).assertExists()
        compose.onNodeWithText("CANAL INVENTADO").performClick()
        compose.onNodeWithText("Variantes", substring = true).assertExists()
        compose.onNodeWithText("Asignar guía").assertExists()
    }

    @Test fun movilBandejaDeRevision() {
        lanzar(tv = false)
        compose.onNodeWithTag("nav_REVISAR").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Por revisar", substring = true).assertExists()
        compose.onNodeWithText("CANAL INVENTADO (M+) no tiene guía", substring = true).assertExists()
    }

    @Test fun tvNavegacionConDpad() {
        lanzar(tv = true)
        compose.onNodeWithTag("nav_RESUMEN").requestFocus()
        compose.onNodeWithTag("nav_RESUMEN").assertIsFocused()
        compose.onNodeWithTag("nav_RESUMEN").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("nav_LISTA").assertIsFocused()
        compose.onNodeWithTag("nav_LISTA").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("Vista previa", substring = true).assertExists()
    }
}
