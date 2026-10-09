package es.cazique.iptvgestor.datos

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import es.cazique.iptvgestor.core.ConfigMotor
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.cazique.iptvgestor.core.CacheHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val Context.almacen: DataStore<Preferences> by preferencesDataStore(name = "ajustes")

/** Ajustes no sensibles en DataStore. Se conservan entre actualizaciones de la app. */
class Ajustes(private val context: Context) {
    private val ds get() = context.almacen

    object K {
        val NOTA_PRUEBA = stringPreferencesKey("nota_prueba")
        val CANAL_PRUEBAS = booleanPreferencesKey("canal_pruebas")
        val COMPROBAR_AUTO = booleanPreferencesKey("comprobar_auto")
        val ULTIMA_COMPROBACION = longPreferencesKey("ultima_comprobacion")
        val ULTIMA_VERSION_VISTA = longPreferencesKey("ultima_version_vista")
        val ETAG_URL = stringPreferencesKey("etag_url")
        val ETAG = stringPreferencesKey("etag")
        val ETAG_CUERPO = stringPreferencesKey("etag_cuerpo")
        val CONFIG_MOTOR = stringPreferencesKey("config_motor")
        val FORMATO = stringPreferencesKey("formato_stream")
        val USER_AGENT = stringPreferencesKey("user_agent")
        val VARIANTE_EPG = stringPreferencesKey("variante_epg")
        val MODO_EXPORTACION = stringPreferencesKey("modo_exportacion")
        val CARPETA_EXPORTACION = stringPreferencesKey("carpeta_exportacion")
        val EXPORTAR_AUTO = booleanPreferencesKey("exportar_auto")
        val AVISO_EXPORTAR_VISTO = booleanPreferencesKey("aviso_exportar_visto")
        val NUMERAR = booleanPreferencesKey("numerar_tvg_chno")
        val EXTVLCOPT = booleanPreferencesKey("extvlcopt_user_agent")
        val SINCRONIZAR_AUTO = booleanPreferencesKey("sincronizar_auto")
        val HORAS_SINCRONIZACION = intPreferencesKey("horas_sincronizacion")
        val ULTIMA_SINCRONIZACION = longPreferencesKey("ultima_sincronizacion")
        val ULTIMA_EPG = longPreferencesKey("ultima_epg")
        val ESTADO_SINCRONIZACION = stringPreferencesKey("estado_sincronizacion")
        val HOSTS_HTTP_PERMITIDOS = stringSetPreferencesKey("hosts_http_permitidos")
        val URL_EFECTIVA = stringPreferencesKey("url_efectiva")
        val PIN_ACTIVO = booleanPreferencesKey("pin_activo")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val EXPORTAR_ADULTOS = booleanPreferencesKey("exportar_adultos")
        val ESPERA_ENLACES = intPreferencesKey("espera_enlaces_s")
        val SERVIDOR_RED = booleanPreferencesKey("servidor_red_local")
        val SERVIDOR_TOKEN = stringPreferencesKey("servidor_token")
        val SUBIR_AUTO = booleanPreferencesKey("subir_auto")
    }

    val datos: Flow<Preferences> = ds.data

    suspend fun configMotor(): ConfigMotor =
        leer(K.CONFIG_MOTOR)?.let { runCatching { JSON.decodeFromString(ConfigMotor.serializer(), it) }.getOrNull() } ?: ConfigMotor()

    val configMotorFlujo: Flow<ConfigMotor> = ds.data.map { p ->
        p[K.CONFIG_MOTOR]?.let { runCatching { JSON.decodeFromString(ConfigMotor.serializer(), it) }.getOrNull() } ?: ConfigMotor()
    }

    suspend fun guardarConfigMotor(c: ConfigMotor) = guardar(K.CONFIG_MOTOR, JSON.encodeToString(ConfigMotor.serializer(), c))

    suspend fun quitar(clave: Preferences.Key<*>) { ds.edit { it.remove(clave) } }

    suspend fun borrarTodo() { ds.edit { it.clear() } }

    val notaPrueba: Flow<String> = ds.data.map { it[K.NOTA_PRUEBA] ?: "" }
    val canalPruebas: Flow<Boolean> = ds.data.map { it[K.CANAL_PRUEBAS] ?: false }
    val comprobarAuto: Flow<Boolean> = ds.data.map { it[K.COMPROBAR_AUTO] ?: true }

    suspend fun <T> leer(clave: Preferences.Key<T>): T? = ds.data.first()[clave]
    suspend fun <T> guardar(clave: Preferences.Key<T>, valor: T) { ds.edit { it[clave] = valor } }

    /** Caché de ETag para las peticiones condicionales a la API de GitHub. */
    fun cacheHttp(): CacheHttp = object : CacheHttp {
        override fun leer(url: String): Pair<String, String>? = runBlocking {
            val p = ds.data.first()
            if (p[K.ETAG_URL] == url) p[K.ETAG]?.let { e -> p[K.ETAG_CUERPO]?.let { e to it } } else null
        }

        override fun guardar(url: String, etag: String, cuerpo: String) = runBlocking {
            ds.edit { it[K.ETAG_URL] = url; it[K.ETAG] = etag; it[K.ETAG_CUERPO] = cuerpo }
            Unit
        }
    }
}
