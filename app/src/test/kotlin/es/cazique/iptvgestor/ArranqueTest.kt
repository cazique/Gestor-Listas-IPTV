package es.cazique.iptvgestor

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import es.cazique.iptvgestor.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Arranca la actividad real, como al abrir la app desde el icono (con la comprobación de actualizaciones). */
@RunWith(RobolectricTestRunner::class)
class ArranqueTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test @Config(sdk = [35])
    fun arrancaSinCerrarse() {
        compose.waitForIdle()
        compose.onNodeWithTag("nav_RESUMEN").assertExists()
        compose.onNodeWithTag("nav_AJUSTES").assertExists()
    }
}
