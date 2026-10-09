package es.cazique.iptvgestor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import es.cazique.iptvgestor.actualizacion.EstadoActualizacion
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import es.cazique.iptvgestor.IptvGestorApp

/** Destinos principales: barra inferior en el teléfono y menú lateral en la TV (sección 6.3). */
enum class Seccion(val titulo: String, val simbolo: String) {
    RESUMEN("Resumen", "⌂"),
    LISTA("Lista", "☰"),
    REVISAR("Revisar", "✓"),
    EXPORTAR("Exportar", "⇪"),
    AJUSTES("Ajustes", "⚙"),
}

/** Pantallas secundarias que se apilan sobre la sección. */
sealed interface Destino {
    data class Principal(val seccion: Seccion) : Destino
    data class Ficha(val ambito: String, val clave: String) : Destino
    data object Cuenta : Destino
    data object Paquetes : Destino
    data object Historial : Destino
    data object Informes : Destino
    data object Actualizaciones : Destino
    data object Utilidades : Destino
}

class Navegador {
    val pila = mutableStateListOf<Destino>(Destino.Principal(Seccion.RESUMEN))
    val actual: Destino get() = pila.last()
    val seccion: Seccion get() = pila.filterIsInstance<Destino.Principal>().lastOrNull()?.seccion ?: Seccion.RESUMEN
    fun ir(d: Destino) { pila.add(d) }
    fun seccion(s: Seccion) { pila.clear(); pila.add(Destino.Principal(s)) }
    fun atras(): Boolean = if (pila.size > 1) { pila.removeAt(pila.lastIndex); true } else false
}

val LocalApp = staticCompositionLocalOf<IptvGestorApp> { error("Sin app") }
val LocalTv = staticCompositionLocalOf { false }
val LocalNav = staticCompositionLocalOf<Navegador> { error("Sin navegador") }

@Composable
fun AppRaiz(
    app: IptvGestorApp,
    tv: Boolean,
    buscarActualizaciones: Boolean,
    comprobarAlAbrir: Boolean = true,
    abrirIntent: (android.content.Intent) -> Unit,
) {
    val nav = remember { Navegador() }
    var actualizadaA by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = nav.pila.size > 1) { nav.atras() }
    LaunchedEffect(Unit) {
        // Tras actualizar: "Actualizada a la versión X". Al abrir: comprobación (como mucho cada 12 h).
        actualizadaA = app.actualizaciones.versionRecienActualizada()
        if (buscarActualizaciones) nav.ir(Destino.Actualizaciones)
        else if (comprobarAlAbrir) {
            app.actualizaciones.comprobarSiToca()
            if (app.actualizaciones.estado.value is EstadoActualizacion.Disponible) nav.ir(Destino.Actualizaciones)
        }
    }
    actualizadaA?.let { v ->
        AlertDialog(
            onDismissRequest = { actualizadaA = null },
            title = { Text("Actualizada a la versión $v") },
            text = { Text("Tus ajustes y decisiones se han conservado.") },
            confirmButton = { TextButton(onClick = { actualizadaA = null }) { Text("Aceptar") } },
        )
    }
    CompositionLocalProvider(LocalApp provides app, LocalTv provides tv, LocalNav provides nav) {
        val contenido: @Composable () -> Unit = {
            when (val d = nav.actual) {
                is Destino.Principal -> when (d.seccion) {
                    Seccion.RESUMEN -> PantallaResumen()
                    Seccion.LISTA -> PantallaLista()
                    Seccion.REVISAR -> PantallaRevisar()
                    Seccion.EXPORTAR -> PantallaExportar(abrirIntent)
                    Seccion.AJUSTES -> PantallaAjustes(abrirIntent)
                }
                is Destino.Ficha -> PantallaFicha(d.ambito, d.clave)
                Destino.Cuenta -> PantallaCuenta()
                Destino.Paquetes -> PantallaPaquetes()
                Destino.Historial -> PantallaHistorial()
                Destino.Informes -> PantallaInformes(abrirIntent)
                Destino.Utilidades -> PantallaUtilidades()
                Destino.Actualizaciones -> PantallaPrincipal(app, tv, buscarActualizaciones, abrirIntent)
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (tv) {
                Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NavigationRail {
                        Seccion.entries.forEach { s ->
                            NavigationRailItem(
                                selected = nav.seccion == s,
                                onClick = { nav.seccion(s) },
                                icon = { Text(s.simbolo, style = MaterialTheme.typography.titleLarge) },
                                label = { Text(s.titulo) },
                                modifier = Modifier.testTag("nav_${s.name}"),
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) { contenido() }
                }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Seccion.entries.forEach { s ->
                                NavigationBarItem(
                                    selected = nav.seccion == s,
                                    onClick = { nav.seccion(s) },
                                    icon = { Text(s.simbolo, style = MaterialTheme.typography.titleLarge) },
                                    label = { Text(s.titulo) },
                                    modifier = Modifier.testTag("nav_${s.name}"),
                                )
                            }
                        }
                    },
                ) { relleno ->
                    Box(Modifier.fillMaxSize().padding(relleno)) { contenido() }
                }
            }
        }
    }
}
