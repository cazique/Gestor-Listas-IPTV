package es.cazique.iptvgestor.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta de la app: azul del icono, superficies azul noche y ámbar como acento (bueno para TV y móvil). */
object Paleta {
    val Primario = Color(0xFF7CB7FF)
    val PrimarioOscuro = Color(0xFF0B3D7A)
    val Acento = Color(0xFFFFC46B)
    val Fondo = Color(0xFF0E1320)
    val Superficie = Color(0xFF161D2D)
    val SuperficieAlta = Color(0xFF1E2740)
    val Borde = Color(0xFF2C3754)
    val Texto = Color(0xFFE7ECF7)
    val TextoSuave = Color(0xFFA3AEC6)
    val Ok = Color(0xFF6BD49A)
    val Error = Color(0xFFFF8A80)
}

private val Colores = darkColorScheme(
    primary = Paleta.Primario,
    onPrimary = Color(0xFF00213F),
    primaryContainer = Paleta.PrimarioOscuro,
    onPrimaryContainer = Color(0xFFD5E4FF),
    secondary = Paleta.Acento,
    onSecondary = Color(0xFF3E2500),
    secondaryContainer = Color(0xFF5A3D00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Paleta.Ok,
    background = Paleta.Fondo,
    onBackground = Paleta.Texto,
    surface = Paleta.Superficie,
    onSurface = Paleta.Texto,
    surfaceVariant = Paleta.SuperficieAlta,
    onSurfaceVariant = Paleta.TextoSuave,
    surfaceContainer = Paleta.Superficie,
    surfaceContainerHigh = Paleta.SuperficieAlta,
    surfaceContainerLow = Color(0xFF121827),
    outline = Paleta.Borde,
    outlineVariant = Color(0xFF232C44),
    error = Paleta.Error,
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** En TV, textos y zonas de foco más grandes (sección 6.3). */
@Composable
fun TemaApp(tv: Boolean, contenido: @Composable () -> Unit) {
    val base = Typography()
    val escala = if (tv) 1.25f else 1f
    val tipografia = base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = base.headlineSmall.fontSize * escala),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = base.titleLarge.fontSize * escala),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = base.titleMedium.fontSize * escala),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * escala),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * escala),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * escala, color = Paleta.TextoSuave),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = base.labelLarge.fontSize * escala),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * escala),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = if (tv) 40.sp else 30.sp),
    )
    MaterialTheme(colorScheme = Colores, typography = tipografia, shapes = Formas, content = contenido)
}
