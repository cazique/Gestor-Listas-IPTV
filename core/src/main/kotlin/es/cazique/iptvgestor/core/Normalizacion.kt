package es.cazique.iptvgestor.core

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Expresión regular con clases de caracteres Unicode (`\w`, `\b`, `\d`, `\s`), como en Python 3.
 * Se usa el indicador UNICODE_CHARACTER_CLASS y no el modificador en línea `(?U)`, que el motor ICU
 * de Android no admite (PatternSyntaxException en Android 16). En Android el indicador se ignora
 * porque ICU ya trabaja siempre en Unicode.
 */
internal fun reU(patron: String): Regex = Pattern.compile(patron, Pattern.UNICODE_CHARACTER_CLASS).toRegex()

/**
 * Reglas de la sección 4.2 y 4.3 de SPEC.md, portadas de `tools/capas_epg.py`.
 * Las expresiones se compilan con [reU] para que `\w`, `\b` y `\s` sean Unicode, como en Python 3.
 */
object Normalizacion {

    /** Preferencia por defecto: la primera va a la capa 1. */
    val PERFIL_NORMAL = listOf("RAW", "UHD", "FHD", "HD", "HEVC", "SD", "LOW")

    /** Perfil "ligero" para reproductores o redes justas. */
    val PERFIL_LIGERO = listOf("HD", "FHD", "HEVC", "RAW", "UHD", "SD", "LOW")

    private val ETIQUETAS = reU(
        "\\b(UHD|FHD|HD|SD|RAW|HEVC|LOW|4K|8K|ULTRA|HDR|60FPS|VIP|H265|H264|BK|" +
            "3840P|2160P|1080P|720P)\\b"
    )
    private val PARENTESIS = Regex("\\(.*?\\)")
    private val PREFIJO = reU("^\\s*[A-Z0-9+]{1,3}\\s*:\\s*")
    private val SIMBOLOS = reU("[^\\w+ ]")
    private val ESPACIOS = reU("\\s+")
    private val MOVISTAR = Regex("^(MOVISTAR PLUS\\+?|MOVISTAR\\+?|M\\+?) ")
    private val BK = reU("\\bBK\\b")
    private val NUMEROS = reU("\\d+")

    /** NFKD, sin marcas diacríticas y en mayúsculas (superíndices a letras normales). */
    fun limpio(nombre: String): String {
        val n = Normalizer.normalize(nombre, Normalizer.Form.NFKD)
        val sb = StringBuilder(n.length)
        var i = 0
        while (i < n.length) {
            val cp = n.codePointAt(i)
            if (Character.getType(cp) != Character.NON_SPACING_MARK.toInt()) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().uppercase()
    }

    /** Clave del canal lógico: quita lo que varía entre copias del mismo canal. */
    fun clave(nombre: String): String {
        var n = PARENTESIS.replace(limpio(nombre), " ")
        n = PREFIJO.replaceFirst(n, "")
        n = ETIQUETAS.replace(n, " ")
        n = SIMBOLOS.replace(n, " ")
        n = ESPACIOS.replace(n, " ").trim()
        return MOVISTAR.replaceFirst(n, "M+ ")
    }

    /** Nombre completo normalizado (sin quitar calidades): identidad secundaria de una variante. */
    fun nombreNormalizado(nombre: String): String =
        ESPACIOS.replace(SIMBOLOS.replace(limpio(nombre), " "), " ").trim()

    /**
     * Posición de calidad (menor = mejor). Sin etiqueta se trata como HD;
     * `SOLO EVENTOS` y `HDR` van siempre al final.
     */
    fun puntos(nombre: String, preferencia: List<String> = PERFIL_NORMAL): Int {
        val n = limpio(nombre)
        var p = -1
        preferencia.forEachIndexed { i, t ->
            if (reU("\\b${Regex.escape(t)}\\b").containsMatchIn(n)) p = maxOf(p, i)
        }
        if (p < 0) p = preferencia.indexOf("HD").coerceAtLeast(0)
        if ("ULTRA" in n) p = minOf(p, 0)
        if ("HDR" in n || "SOLO EVENTOS" in n) p += 10
        return p
    }

    /** Variante marcada como respaldo (`BK`) en su nombre o en el de su grupo. */
    fun esRespaldo(nombre: String, grupo: String): Boolean =
        BK.containsMatchIn(limpio(nombre)) || BK.containsMatchIn(limpio(grupo))

    fun compacto(clave: String): String = clave.replace(" ", "")

    fun numeros(texto: String): List<String> = NUMEROS.findAll(texto).map { it.value }.toList()

    /** Entrada separadora del proveedor, como `#### ORANGE ᴿᴬᵂ ####`. */
    fun esSeparador(nombre: String): Boolean = nombre.trim().startsWith("#")
}
