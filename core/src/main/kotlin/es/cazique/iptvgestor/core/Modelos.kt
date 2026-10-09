package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.InputStream

/** Categoría (grupo) del proveedor, tal como la devuelve `get_live_categories`. */
@Serializable
data class Categoria(val id: String, val nombre: String, val orden: Int = 0)

/** Canal en directo del proveedor, tal como lo devuelve `get_live_streams` (solo los campos útiles). */
@Serializable
data class Stream(
    val streamId: Long,
    val num: Long,
    val nombre: String,
    val categoriaId: String,
    val icono: String = "",
    val epgChannelId: String = "",
    val adulto: Boolean = false,
    val anadido: Long = 0,
)

/** Lectura tolerante del JSON de Xtream Codes (los tipos varían entre proveedores: "1" frente a 1). */
object XtreamJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun categorias(texto: String): List<Categoria> = categorias(json.parseToJsonElement(texto))

    fun categorias(el: JsonElement): List<Categoria> =
        lista(el).mapIndexedNotNull { i, e ->
            val o = e as? JsonObject ?: return@mapIndexedNotNull null
            val id = o.texto("category_id") ?: return@mapIndexedNotNull null
            Categoria(id, o.texto("category_name") ?: "", i)
        }

    fun streams(texto: String): List<Stream> = streams(json.parseToJsonElement(texto))

    fun streams(entrada: InputStream): List<Stream> =
        entrada.bufferedReader(Charsets.UTF_8).use { streams(it.readText()) }

    fun streams(el: JsonElement): List<Stream> =
        lista(el).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.texto("stream_id")?.toLongOrNull() ?: return@mapNotNull null
            Stream(
                streamId = id,
                num = o.texto("num")?.toLongOrNull() ?: 0,
                nombre = o.texto("name") ?: "",
                categoriaId = o.texto("category_id") ?: "",
                icono = o.texto("stream_icon") ?: "",
                epgChannelId = o.texto("epg_channel_id") ?: "",
                adulto = o.texto("is_adult").let { it == "1" || it.equals("true", true) },
                anadido = o.texto("added")?.toLongOrNull() ?: 0,
            )
        }

    private fun lista(el: JsonElement): List<JsonElement> = when (el) {
        is JsonArray -> el
        is JsonObject -> el.values.toList() // algunos paneles devuelven un objeto indexado
        else -> emptyList()
    }

    internal fun JsonObject.texto(campo: String): String? = when (val v = this[campo]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        else -> v.toString()
    }
}

/** Estado de la cuenta (`player_api.php` sin `action`). */
@Serializable
data class EstadoCuenta(
    val estado: String,
    val caducidad: Long?,
    val conexionesMaximas: Int?,
    val conexionesActivas: Int?,
    val httpsPort: Int?,
    val autenticado: Boolean,
) {
    companion object {
        fun desdeJson(texto: String): EstadoCuenta {
            val raiz = XtreamJson.json.parseToJsonElement(texto).jsonObject
            val u = raiz["user_info"] as? JsonObject ?: JsonObject(emptyMap())
            val s = raiz["server_info"] as? JsonObject ?: JsonObject(emptyMap())
            with(XtreamJson) {
                return EstadoCuenta(
                    estado = u.texto("status") ?: "desconocido",
                    caducidad = u.texto("exp_date")?.toLongOrNull(),
                    conexionesMaximas = u.texto("max_connections")?.toIntOrNull(),
                    conexionesActivas = u.texto("active_cons")?.toIntOrNull(),
                    httpsPort = s.texto("https_port")?.toIntOrNull(),
                    autenticado = u.texto("auth") == "1" || u.texto("status") != null,
                )
            }
        }
    }
}
