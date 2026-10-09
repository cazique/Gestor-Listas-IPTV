package es.cazique.iptvgestor.core

import org.junit.Assume
import org.kxml2.io.KXmlParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

/** Acceso compartido a los fixtures y a la guía de dobleM (descargada, nunca guardada en el repositorio). */
object Datos {
    private val dir = File(System.getProperty("fixtures.dir") ?: "../fixtures")

    val categorias: List<Categoria> by lazy { XtreamJson.categorias(File(dir, "live_categorias.json").readText()) }
    val streams: List<Stream> by lazy { XtreamJson.streams(File(dir, "live.json").readText()) }

    /** La guía real: de `EPG_LOCAL`, de la caché de compilación o descargándola. Si no hay red, la prueba se omite. */
    val epg: List<CanalEpg>? by lazy {
        val local = System.getProperty("epg.local")?.let(::File)?.takeIf { it.exists() }
        val cache = File(System.getProperty("epg.cache") ?: "build/epg", "guiatv_sincolor.xml.gz")
        val archivo = local ?: cache.takeIf { it.exists() && it.length() > 0 } ?: try {
            cache.parentFile.mkdirs()
            val c = URI(FuentesEpg.PREDETERMINADA).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 60000
            // Descarga a un temporal y renombra: una descarga cortada no deja una caché corrupta.
            val parcial = File(cache.parentFile, cache.name + ".part")
            c.inputStream.use { i -> parcial.outputStream().use { i.copyTo(it) } }
            parcial.renameTo(cache)
            cache
        } catch (e: Exception) {
            println("Guía no disponible (${e.javaClass.simpleName}); se omiten las pruebas que la necesitan")
            null
        }
        archivo?.let { f ->
            try {
                GZIPInputStream(f.inputStream()).use { leerEpg(it) }
            } catch (e: Exception) {
                println("Guía ilegible (${e.javaClass.simpleName}); se omiten las pruebas que la necesitan")
                f.delete()
                null
            }
        }
    }

    fun leerEpg(input: java.io.InputStream): List<CanalEpg> {
        val p = KXmlParser()
        p.setInput(input, "UTF-8")
        return LectorEpg.leer(p)
    }

    fun epgObligatoria(): List<CanalEpg> {
        Assume.assumeTrue("Sin guía de dobleM (sin red)", epg != null)
        return epg!!
    }
}
