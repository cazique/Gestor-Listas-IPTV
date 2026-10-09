package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable
import org.xmlpull.v1.XmlPullParser

/** Canal de la guía XMLTV (solo cabecera: id, alias e icono). La posición es el orden de canales. */
@Serializable
data class CanalEpg(val id: String, val nombres: List<String>, val icono: String, val posicion: Int)

object FuentesEpg {
    private const val BASE = "https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/"

    /** Las seis variantes `sincolor` que dobleM recomienda para TiviMate (sección 4.6). */
    val VARIANTES: List<Pair<String, String>> = listOf(
        "guiatv_sincolor" to "Con caracteres especiales; año, edad y valoración en el título",
        "guiatv_sincolor1" to "Género, año, edad y valoración en la descripción",
        "guiatv_sincolor0" to "Subtítulo, año, edad y valoración en la descripción",
        "guiatv_sincolor2" to "Como sincolor, sin caracteres especiales",
        "guiatv_sincolor3" to "Como sincolor1, sin caracteres especiales",
        "guiatv_sincolor4" to "Como sincolor0, sin caracteres especiales",
    )

    fun url(variante: String): String = "$BASE$variante.xml.gz"

    val PREDETERMINADA: String = url("guiatv_sincolor")
}

/**
 * Lee los `<channel>` de un XMLTV en streaming y se detiene en el primer `<programme>`.
 * El analizador lo aporta quien llama (`Xml.newPullParser()` en Android, kxml2 en las pruebas).
 */
object LectorEpg {
    fun leer(parser: XmlPullParser): List<CanalEpg> {
        val canales = ArrayList<CanalEpg>(700)
        var id: String? = null
        var nombres = ArrayList<String>()
        var icono = ""
        var evento = parser.eventType
        while (evento != XmlPullParser.END_DOCUMENT) {
            if (evento == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> return canales
                    "channel" -> { id = parser.getAttributeValue(null, "id"); nombres = ArrayList(); icono = "" }
                    "display-name" -> if (id != null) nombres.add(parser.nextText() ?: "")
                    "icon" -> if (id != null && icono.isEmpty()) icono = parser.getAttributeValue(null, "src") ?: ""
                }
            } else if (evento == XmlPullParser.END_TAG && parser.name == "channel") {
                id?.let { canales.add(CanalEpg(it, nombres, icono, canales.size)) }
                id = null
            }
            evento = parser.next()
        }
        return canales
    }
}

/** Alias incorporados (editables por el usuario): sustituciones y equivalencias exactas. */
@Serializable
data class AliasEpg(
    val sinonimos: List<Pair<String, String>> = SINONIMOS,
    val exactos: Map<String, String> = EXACTOS,
) {
    companion object {
        val SINONIMOS = listOf(
            "LCAMPEONES" to "LIGA DE CAMPEONES", "LA LIGA" to "LALIGA",
            "DISNEY JR" to "DISNEY JUNIOR", "NAT GEOGRAPHIC" to "NATIONAL GEOGRAPHIC",
            "CLAN TVE" to "CLAN", "DISCOVERY CHANEL" to "DISCOVERY",
            "DISCOVERY CHANNEL" to "DISCOVERY", "HOLLYWOOD" to "CANAL HOLLYWOOD",
        )
        val EXACTOS = mapOf("VAMOS" to "M+ VAMOS", "M+ DEPORTES 1" to "M+ DEPORTES", "M+ CINE" to "M+ CINE ESPANOL")

        /** Formato simple `NOMBRE = NOMBRE EN LA GUÍA` (compatible con `alias_epg.txt`). */
        fun leerAliasUsuario(texto: String): Map<String, String> =
            texto.lineSequence()
                .map { it.trim() }
                .filter { "=" in it && !it.startsWith("#") }
                .associate { l ->
                    val (a, b) = l.split("=", limit = 2)
                    Normalizacion.clave(a) to Normalizacion.clave(b)
                }
    }
}

enum class MetodoEmparejado { MANUAL, ALIAS, EXACTO, SINONIMO, COMPACTO, APROXIMADO }

data class Emparejado(val canal: CanalEpg, val metodo: MetodoEmparejado, val confianza: Double)

/**
 * Emparejado de un canal lógico con la guía (`buscar_epg()` de la sección 15):
 * alias del usuario, nombre exacto, sinónimos, nombre compactado y coincidencia aproximada
 * (umbral 0,88) exigiendo que coincidan los números.
 */
class Emparejador(
    val epg: List<CanalEpg>,
    private val alias: AliasEpg = AliasEpg(),
    private val aliasUsuario: Map<String, String> = emptyMap(),
) {
    private val indice = LinkedHashMap<String, Int>()
    private val comp = LinkedHashMap<String, Int>()
    private val sinonimos = alias.sinonimos.map { (a, b) -> reU("\\b${Regex.escape(a)}\\b") to b }
    private val porId = epg.associateBy { it.id }

    init {
        epg.forEachIndexed { pos, c ->
            for (n in c.nombres) {
                val k = Normalizacion.clave(n)
                if (k.isNotEmpty()) indice.putIfAbsent(k, pos)
            }
        }
        // Como el diccionario por comprensión de Python: la última posición gana, el orden de inserción se mantiene.
        for ((k, p) in indice) comp[Normalizacion.compacto(k)] = p
    }

    fun canalPorId(id: String): CanalEpg? = porId[id]

    fun buscar(clave: String): Emparejado? {
        aliasUsuario[clave]?.let { destino -> indice[destino]?.let { return Emparejado(epg[it], MetodoEmparejado.ALIAS, 1.0) } }
        indice[clave]?.let { return Emparejado(epg[it], MetodoEmparejado.EXACTO, 1.0) }
        var k2 = alias.exactos[clave] ?: clave
        for ((re, b) in sinonimos) k2 = re.replace(k2, Regex.escapeReplacement(b))
        indice[k2]?.let { return Emparejado(epg[it], MetodoEmparejado.SINONIMO, 0.95) }
        val c = Normalizacion.compacto(k2)
        comp[c]?.let { return Emparejado(epg[it], MetodoEmparejado.COMPACTO, 0.9) }
        val numeros = Normalizacion.numeros(c)
        for ((puntuacion, m) in Difflib.coincidenciasCercanas(c, comp.keys, 3, 0.88)) {
            if (Normalizacion.numeros(m) == numeros) return Emparejado(epg[comp.getValue(m)], MetodoEmparejado.APROXIMADO, puntuacion)
        }
        return null
    }

    /** Las mejores candidatas aproximadas para la bandeja de revisión (sin exigir números). */
    fun candidatas(clave: String, n: Int = 3, umbral: Double = 0.5): List<Pair<CanalEpg, Double>> {
        val vistos = HashSet<Int>()
        return Difflib.coincidenciasCercanas(Normalizacion.compacto(clave), comp.keys, n * 3, umbral)
            .mapNotNull { (p, m) -> comp.getValue(m).takeIf { vistos.add(it) }?.let { epg[it] to p } }
            .take(n)
    }

    /** Búsqueda libre para el selector manual de guía. */
    fun buscarTexto(texto: String, max: Int = 50): List<CanalEpg> {
        val q = Normalizacion.clave(texto)
        if (q.isEmpty()) return epg.take(max)
        val qc = Normalizacion.compacto(q)
        return epg.filter { c ->
            c.id.uppercase().contains(q) || c.nombres.any { Normalizacion.compacto(Normalizacion.clave(it)).contains(qc) }
        }.take(max)
    }
}

/** Equivalente exacto de `difflib.SequenceMatcher.ratio()` y `get_close_matches()` de Python (sin basura). */
object Difflib {
    fun ratio(a: String, b: String): Double {
        val total = a.length + b.length
        if (total == 0) return 1.0
        return 2.0 * coincidencias(a, b) / total
    }

    /** Suma de los bloques coincidentes (Ratcliff/Obershelp) con `a` = candidata y `b` = palabra. */
    private fun coincidencias(a: String, b: String): Int {
        val b2j = HashMap<Char, MutableList<Int>>()
        b.forEachIndexed { j, ch -> b2j.getOrPut(ch) { ArrayList() }.add(j) }
        // Con cadenas de menos de 200 caracteres Python no aplica "autojunk".
        fun masLarga(alo: Int, ahi: Int, blo: Int, bhi: Int): Triple<Int, Int, Int> {
            var besti = alo; var bestj = blo; var bestsize = 0
            var j2len = HashMap<Int, Int>()
            for (i in alo until ahi) {
                val nuevo = HashMap<Int, Int>()
                for (j in b2j[a[i]] ?: emptyList()) {
                    if (j < blo) continue
                    if (j >= bhi) break
                    val k = (j2len[j - 1] ?: 0) + 1
                    nuevo[j] = k
                    if (k > bestsize) { besti = i - k + 1; bestj = j - k + 1; bestsize = k }
                }
                j2len = nuevo
            }
            return Triple(besti, bestj, bestsize)
        }
        var total = 0
        val cola = ArrayDeque<IntArray>()
        cola.add(intArrayOf(0, a.length, 0, b.length))
        while (cola.isNotEmpty()) {
            val (alo, ahi, blo, bhi) = cola.removeLast()
            val (i, j, k) = masLarga(alo, ahi, blo, bhi)
            if (k > 0) {
                total += k
                if (alo < i && blo < j) cola.add(intArrayOf(alo, i, blo, j))
                if (i + k < ahi && j + k < bhi) cola.add(intArrayOf(i + k, ahi, j + k, bhi))
            }
        }
        return total
    }

    /** `get_close_matches(palabra, posibilidades, n, cutoff)`: orden por puntuación y, a igualdad, por texto descendente. */
    fun coincidenciasCercanas(palabra: String, posibilidades: Collection<String>, n: Int, umbral: Double): List<Pair<Double, String>> =
        posibilidades.asSequence()
            .map { ratio(it, palabra) to it }
            .filter { it.first >= umbral }
            .sortedWith(compareByDescending<Pair<Double, String>> { it.first }.thenByDescending { it.second })
            .take(n)
            .toList()
}
