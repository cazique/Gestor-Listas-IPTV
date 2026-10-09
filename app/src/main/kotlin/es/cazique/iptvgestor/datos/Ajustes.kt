package es.cazique.iptvgestor.datos

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.cazique.iptvgestor.core.CacheHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

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
    }

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
