package es.cazique.iptvgestor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val Colores = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    secondary = Color(0xFFFFCC80),
    background = Color(0xFF101418),
    surface = Color(0xFF161B21),
)

/** En TV, textos y zonas de foco más grandes (sección 6.3). */
@Composable
fun TemaApp(tv: Boolean, contenido: @Composable () -> Unit) {
    val base = Typography()
    val tipografia = if (!tv) base else base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 22.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 20.sp),
        titleLarge = base.titleLarge.copy(fontSize = 30.sp),
        titleMedium = base.titleMedium.copy(fontSize = 24.sp),
        labelLarge = base.labelLarge.copy(fontSize = 20.sp),
    )
    MaterialTheme(colorScheme = Colores, typography = tipografia, content = contenido)
}
