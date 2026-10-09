package es.cazique.iptvgestor.core

import java.net.URLEncoder

/** Datos de acceso al proveedor. Nunca se registran: usar [Redactor] antes de mostrar nada. */
data class Cuenta(val host: String, val usuario: String, val contrasena: String) {
    /** Host sin barra final y con esquema (http:// si no se indicó). */
    val base: String
        get() {
            val h = host.trim().trimEnd('/')
            return if (h.startsWith("http://", true) || h.startsWith("https://", true)) h else "http://$h"
        }

    override fun toString(): String = "Cuenta(host=${Redactor.host(host)}, usuario=***, contrasena=***)"
}

enum class ModoExportacion { TODO, POR_PAQUETE, POR_CAPA }

data class OpcionesM3u(
    val formato: String = "ts",
    val urlGuia: String = FuentesEpg.PREDETERMINADA,
    val numerar: Boolean = false,
    val userAgent: String? = null,
    val incluirAdultos: Boolean = true,
)

data class ArchivoM3u(val nombre: String, val contenido: String, val entradas: Int, val idsQuitadosPorRepeticion: Int)

/** Generador de la lista M3U (secciones 4.5 y 7.4). */
object GeneradorM3u {

    fun cabecera(urlGuia: String): String {
        val u = atributo(urlGuia)
        return "#EXTM3U url-tvg=\"$u\" x-tvg-url=\"$u\""
    }

    fun urlStream(cuenta: Cuenta, streamId: Long, formato: String): String =
        "${cuenta.base}/live/${seg(cuenta.usuario)}/${seg(cuenta.contrasena)}/$streamId.$formato"

    private fun seg(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Sanea un valor de atributo: sin comillas dobles ni saltos de línea ni caracteres de control. */
    fun atributo(valor: String): String =
        valor.map { c ->
            when {
                c == '"' -> '\''
                c == '\r' || c == '\n' || c == '\t' -> ' '
                Character.isISOControl(c) -> ' '
                else -> c
            }
        }.joinToString("").trim()

    fun titulo(valor: String): String = atributo(valor).ifEmpty { "Sin nombre" }

    fun generar(
        entradas: List<EntradaLista>,
        cuenta: Cuenta,
        opciones: OpcionesM3u = OpcionesM3u(),
        tvgIdsUnicos: Boolean = false,
    ): Pair<String, Int> {
        val sb = StringBuilder(entradas.size * 200)
        sb.append(cabecera(opciones.urlGuia)).append('\n')
        val vistos = HashSet<String>()
        var quitados = 0
        var numero = 0
        for (e in entradas) {
            if (!opciones.incluirAdultos && e.canal.adulto) continue
            var id = e.canal.tvgId
            if (tvgIdsUnicos && id.isNotEmpty() && !vistos.add(id)) { id = ""; quitados++ }
            numero++
            sb.append("#EXTINF:-1 tvg-id=\"").append(atributo(id))
                .append("\" tvg-name=\"").append(atributo(e.nombreMostrado))
                .append("\" tvg-logo=\"").append(atributo(e.canal.icono)).append('"')
            if (opciones.numerar) sb.append(" tvg-chno=\"").append(numero).append('"')
            sb.append(" group-title=\"").append(atributo(e.grupoSalida)).append("\",")
                .append(titulo(e.nombreMostrado)).append('\n')
            opciones.userAgent?.takeIf { it.isNotBlank() }?.let { sb.append("#EXTVLCOPT:http-user-agent=").append(atributo(it)).append('\n') }
            sb.append(urlStream(cuenta, e.variante.stream.streamId, opciones.formato)).append('\n')
        }
        return sb.toString() to quitados
    }

    /** Genera uno o varios archivos según el modo. En los modos por archivo, ningún `tvg-id` se repite dentro de un archivo. */
    fun exportar(
        entradas: List<EntradaLista>,
        cuenta: Cuenta,
        modo: ModoExportacion,
        opciones: OpcionesM3u = OpcionesM3u(),
        base: String = "lista",
    ): List<ArchivoM3u> {
        val grupos: Map<String, List<EntradaLista>> = when (modo) {
            ModoExportacion.TODO -> mapOf(base to entradas)
            ModoExportacion.POR_PAQUETE -> entradas.groupBy { it.canal.ambito }.mapKeys { "${base}_${nombreArchivo(it.key)}" }
            ModoExportacion.POR_CAPA -> entradas.groupBy { it.grupoSalida }.mapKeys { "${base}_${nombreArchivo(it.key)}" }
        }
        return grupos.map { (nombre, es) ->
            val (texto, quitados) = generar(es, cuenta, opciones, tvgIdsUnicos = modo != ModoExportacion.TODO)
            ArchivoM3u("$nombre.m3u", texto, es.size, quitados)
        }
    }

    fun nombreArchivo(s: String): String =
        Normalizacion.limpio(s).replace("+", "PLUS").replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').lowercase().ifEmpty { "grupo" }
}

/** Validador de M3U (sección 7.4): falla si alguna línea es inválida. */
object ValidadorM3u {
    private val EXTINF = Regex("^#EXTINF:-?\\d+(?: [A-Za-z0-9-]+=\"[^\"]*\")*,[^\\r\\n]+$")
    private val CABECERA = Regex("^#EXTM3U(?: [A-Za-z0-9-]+=\"[^\"]*\")*$")

    fun validar(texto: String): List<String> {
        val errores = ArrayList<String>()
        if (texto.contains('\r')) errores.add("Contiene retornos de carro (CR)")
        if (texto.indices.any { i ->
                val c = texto[i]
                (c.isHighSurrogate() && (i + 1 >= texto.length || !texto[i + 1].isLowSurrogate())) ||
                    (c.isLowSurrogate() && (i == 0 || !texto[i - 1].isHighSurrogate()))
            }
        ) errores.add("Contiene caracteres que no se pueden codificar en UTF-8")
        val lineas = texto.split('\n').let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        if (lineas.isEmpty() || !CABECERA.matches(lineas[0])) errores.add("Línea 1: falta una cabecera #EXTM3U válida")
        var i = 1
        while (i < lineas.size) {
            val l = lineas[i]
            when {
                l.startsWith("#EXTINF") -> {
                    if (!EXTINF.matches(l)) errores.add("Línea ${i + 1}: #EXTINF mal formado")
                    var j = i + 1
                    while (j < lineas.size && lineas[j].startsWith("#EXTVLCOPT:")) j++
                    val url = lineas.getOrNull(j)
                    if (url == null || url.isBlank() || url.startsWith("#") || !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+$").matches(url)) {
                        errores.add("Línea ${j + 1}: falta la dirección del canal")
                    }
                    i = j + 1
                }
                l.isBlank() -> { errores.add("Línea ${i + 1}: línea vacía"); i++ }
                else -> { errores.add("Línea ${i + 1}: línea inesperada"); i++ }
            }
            if (errores.size > 50) { errores.add("…"); break }
        }
        return errores
    }
}

/** Redacción de credenciales para registros, informes y mensajes de error (sección 9). */
object Redactor {
    private val RUTA = Regex("/(live|movie|series|timeshift)/[^/\\s]+/[^/\\s]+/")
    private val PARAMS = Regex("(?i)(username|password|user|pass)=[^&\\s\"']*")

    fun redactar(texto: String, cuenta: Cuenta? = null): String {
        var t = RUTA.replace(texto) { "/${it.groupValues[1]}/***/***/" }
        t = PARAMS.replace(t) { it.value.substringBefore('=') + "=***" }
        if (cuenta != null) {
            listOf(cuenta.usuario, cuenta.contrasena).filter { it.length >= 3 }.forEach { t = t.replace(it, "***") }
        }
        return t
    }

    fun host(h: String): String = h.trim().let { if (it.isEmpty()) "" else "${it.take(it.indexOf("://").let { i -> if (i < 0) 0 else i + 3 })}***" }
}
