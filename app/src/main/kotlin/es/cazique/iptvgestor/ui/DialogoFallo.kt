package es.cazique.iptvgestor.ui

import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Muestra el último fallo (sin credenciales) para poder compartirlo. */
@Composable
fun DialogoFallo(texto: String, abrir: (Intent) -> Unit) {
    var visible by remember { mutableStateOf(true) }
    if (!visible) return
    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text("La app se cerró por un error") },
        text = {
            Text(texto, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(onClick = {
                abrir(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), "Compartir error"))
                visible = false
            }) { Text("Compartir") }
        },
        dismissButton = { TextButton(onClick = { visible = false }) { Text("Cerrar") } },
    )
}
