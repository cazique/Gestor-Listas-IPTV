package es.cazique.iptvgestor.datos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import es.cazique.iptvgestor.core.ArchivoM3u
import es.cazique.iptvgestor.core.GeneradorM3u
import es.cazique.iptvgestor.core.ModoExportacion
import es.cazique.iptvgestor.core.OpcionesM3u
import es.cazique.iptvgestor.core.ValidadorM3u
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Exportación de la lista (sección 7.1): a una carpeta elegida (SAF) o con el menú compartir. */
class Exportador(private val context: Context, private val repo: Repositorio) {
    private val ajustes get() = repo.ajustes

    suspend fun generar(modo: ModoExportacion? = null): List<ArchivoM3u> = withContext(Dispatchers.Default) {
        val r = repo.resultado.value ?: run { repo.recalcular(); repo.resultado.value } ?: throw IOException("No hay datos: sincroniza primero")
        val cuenta = repo.cuentaEfectiva() ?: throw IOException("Falta configurar la cuenta")
        val ua = ajustes.leer(Ajustes.K.USER_AGENT) ?: Repositorio.UA_PREDETERMINADO
        val opciones = OpcionesM3u(
            formato = ajustes.leer(Ajustes.K.FORMATO) ?: "ts",
            urlGuia = repo.urlGuia(),
            numerar = ajustes.leer(Ajustes.K.NUMERAR) ?: false,
            userAgent = if (ajustes.leer(Ajustes.K.EXTVLCOPT) == true) ua else null,
            incluirAdultos = ajustes.leer(Ajustes.K.EXPORTAR_ADULTOS) ?: true,
        )
        val m = modo ?: runCatching { ModoExportacion.valueOf(ajustes.leer(Ajustes.K.MODO_EXPORTACION) ?: "TODO") }.getOrDefault(ModoExportacion.TODO)
        val archivos = GeneradorM3u.exportar(r.lista, cuenta, m, opciones)
        for (a in archivos) {
            val errores = ValidadorM3u.validar(a.contenido)
            if (errores.isNotEmpty()) throw IOException("La lista ${a.nombre} no pasa el validador: ${errores.first()}")
        }
        archivos
    }

    /** Escribe (o sobrescribe) los archivos en la carpeta elegida con ACTION_OPEN_DOCUMENT_TREE. */
    suspend fun guardarEnCarpeta(carpeta: Uri, archivos: List<ArchivoM3u>, extras: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, carpeta) ?: throw IOException("Carpeta no disponible")
        val todos = archivos.map { it.nombre to it.contenido } + extras.toList()
        for ((nombre, contenido) in todos) {
            val mime = if (nombre.endsWith(".m3u")) "audio/x-mpegurl" else if (nombre.endsWith(".json")) "application/json" else "text/plain"
            val doc = dir.findFile(nombre) ?: dir.createFile(mime, nombre) ?: throw IOException("No se pudo crear $nombre")
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(contenido.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("No se pudo escribir $nombre")
        }
    }

    /** Prepara un Intent para compartir los archivos (por FileProvider, desde la caché). */
    suspend fun intentCompartir(archivos: List<Pair<String, String>>): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exportar").apply { deleteRecursively(); mkdirs() }
        val uris = ArrayList(archivos.map { (nombre, contenido) ->
            val f = File(dir, nombre).apply { writeText(contenido, Charsets.UTF_8) }
            FileProvider.getUriForFile(context, "${context.packageName}.archivos", f)
        })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        Intent.createChooser(intent.setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir")
    }

    /** Exportación automática tras sincronizar (sección 10.7). */
    suspend fun exportarAuto(): String? {
        if (ajustes.leer(Ajustes.K.EXPORTAR_AUTO) != true) return null
        val carpeta = ajustes.leer(Ajustes.K.CARPETA_EXPORTACION)?.let(Uri::parse) ?: return "No hay carpeta de exportación elegida"
        return try {
            val informe = repo.ultimoInforme()?.comoTexto()
            guardarEnCarpeta(carpeta, generar(), informe?.let { mapOf("informe.txt" to it) } ?: emptyMap())
            null
        } catch (e: Exception) { e.message }
    }
}
