package es.cazique.iptvgestor.datos

import android.content.Context
import android.net.Uri
import android.util.Xml
import es.cazique.iptvgestor.core.BandejaRevision
import es.cazique.iptvgestor.core.CanalEpg
import es.cazique.iptvgestor.core.CasoRevision
import es.cazique.iptvgestor.core.Categoria
import es.cazique.iptvgestor.core.ClienteXtream
import es.cazique.iptvgestor.core.Comparador
import es.cazique.iptvgestor.core.Cuenta
import es.cazique.iptvgestor.core.Decision
import es.cazique.iptvgestor.core.Decisiones
import es.cazique.iptvgestor.core.EstadoCuenta
import es.cazique.iptvgestor.core.FuentesEpg
import es.cazique.iptvgestor.core.InformeCambios
import es.cazique.iptvgestor.core.Instantanea
import es.cazique.iptvgestor.core.LectorEpg
import es.cazique.iptvgestor.core.Motor
import es.cazique.iptvgestor.core.PoliticaHttps
import es.cazique.iptvgestor.core.Redactor
import es.cazique.iptvgestor.core.ResultadoHttps
import es.cazique.iptvgestor.core.ResultadoMotor
import es.cazique.iptvgestor.core.Stream
import es.cazique.iptvgestor.core.TipoDecision
import es.cazique.iptvgestor.core.XtreamJson
import es.cazique.iptvgestor.datos.bd.BaseDatos
import es.cazique.iptvgestor.datos.bd.CanalEpgEntidad
import es.cazique.iptvgestor.datos.bd.CategoriaEntidad
import es.cazique.iptvgestor.datos.bd.DecisionEntidad
import es.cazique.iptvgestor.datos.bd.InformeEntidad
import es.cazique.iptvgestor.datos.bd.StreamEntidad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

sealed interface ResultadoConexion {
    data class Ok(val estado: EstadoCuenta, val base: String, val https: Boolean) : ResultadoConexion
    data class NecesitaHttp(val host: String) : ResultadoConexion
    data class Error(val mensaje: String) : ResultadoConexion
}

data class ResumenSincronizacion(val informe: InformeCambios?, val error: String?)

/** Datos de la app: proveedor, guía, decisiones, motor y sincronización (sección 10). */
class Repositorio(
    private val context: Context,
    private val http: OkHttpClient,
    val ajustes: Ajustes,
    val credenciales: Credenciales,
    private val bd: BaseDatos,
) {
    private val dao = bd.dao()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _resultado = MutableStateFlow<ResultadoMotor?>(null)
    val resultado: StateFlow<ResultadoMotor?> = _resultado.asStateFlow()
    private val _casos = MutableStateFlow<List<CasoRevision>>(emptyList())
    val casos: StateFlow<List<CasoRevision>> = _casos.asStateFlow()
    private val _progreso = MutableStateFlow<String?>(null)
    val progreso: StateFlow<String?> = _progreso.asStateFlow()

    val historial: Flow<List<Decision>> = dao.decisionesFlujo().map { l -> l.map { it.aDominio() } }
    val informes: Flow<List<InformeCambios>> = dao.informes().map { l ->
        l.mapNotNull { runCatching { json.decodeFromString(InformeCambios.serializer(), it.json) }.getOrNull() }
    }

    // ---------- Conexión (HTTPS primero, sección 9) ----------

    suspend fun probarConexion(cuenta: Cuenta, permitirHttp: Boolean = false): ResultadoConexion = withContext(Dispatchers.IO) {
        val ua = ajustes.leer(Ajustes.K.USER_AGENT) ?: UA_PREDETERMINADO
        val hostClave = PoliticaHttps.clave(cuenta.host)
        val consentidos = ajustes.leer(Ajustes.K.HOSTS_HTTP_PERMITIDOS) ?: emptySet()
        val rapido = http.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        val escrito = cuenta.host.trim()
        if (escrito.startsWith("https://", ignoreCase = true)) {
            return@withContext try {
                ResultadoConexion.Ok(ClienteXtream(rapido, cuenta, ua).estado(), cuenta.base, true)
            } catch (e: Exception) { ResultadoConexion.Error(Redactor.redactar(e.message ?: "", cuenta)) }
        }
        val (urlHttps, urlHttp) = PoliticaHttps.variantes(escrito)
        val errorHttps = try {
            val estado = ClienteXtream(rapido, cuenta.copy(host = urlHttps), ua).estado()
            return@withContext ResultadoConexion.Ok(estado, urlHttps, true)
        } catch (e: Exception) { e }
        if (PoliticaHttps.clasificar(errorHttps) == ResultadoHttps.ERROR_CERTIFICADO) {
            return@withContext ResultadoConexion.Error("El servidor ofrece HTTPS pero su certificado no es válido. Por seguridad no se cambia a HTTP.")
        }
        if (!permitirHttp && hostClave !in consentidos) return@withContext ResultadoConexion.NecesitaHttp(hostClave)
        ajustes.guardar(Ajustes.K.HOSTS_HTTP_PERMITIDOS, consentidos + hostClave)
        try {
            val estado = ClienteXtream(rapido, cuenta.copy(host = urlHttp), ua).estado()
            // Si el servidor anuncia un puerto HTTPS, se vuelve a intentar HTTPS con él.
            val https = estado.httpsPort?.let { PoliticaHttps.variantes(escrito, it).first }
            if (https != null && https != urlHttps) {
                runCatching { ClienteXtream(rapido, cuenta.copy(host = https), ua).estado() }.getOrNull()?.let {
                    return@withContext ResultadoConexion.Ok(it, https, true)
                }
            }
            ResultadoConexion.Ok(estado, urlHttp, false)
        } catch (e: Exception) {
            ResultadoConexion.Error(Redactor.redactar(e.message ?: e.javaClass.simpleName, cuenta))
        }
    }

    suspend fun guardarCuenta(cuenta: Cuenta, baseEfectiva: String) {
        credenciales.guardar(cuenta)
        ajustes.guardar(Ajustes.K.URL_EFECTIVA, baseEfectiva)
    }

    /** Cuenta con la dirección efectiva (HTTPS si se pudo) para la API y la lista exportada. */
    suspend fun cuentaEfectiva(): Cuenta? {
        val c = credenciales.leer() ?: return null
        val base = ajustes.leer(Ajustes.K.URL_EFECTIVA) ?: return c
        if (base.startsWith("http://")) {
            val ok = PoliticaHttps.clave(c.host) in (ajustes.leer(Ajustes.K.HOSTS_HTTP_PERMITIDOS) ?: emptySet())
            if (!ok) return c.copy(host = base.replaceFirst("http://", "https://"))
        }
        return c.copy(host = base)
    }

    // ---------- Sincronización ----------

    suspend fun sincronizar(): ResumenSincronizacion = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val cuenta = cuentaEfectiva() ?: return@withContext ResumenSincronizacion(null, "Falta configurar la cuenta")
                val ua = ajustes.leer(Ajustes.K.USER_AGENT) ?: UA_PREDETERMINADO
                val cliente = ClienteXtream(http, cuenta, ua)
                _progreso.value = "Descargando categorías…"
                val categorias = cliente.categorias()
                _progreso.value = "Descargando canales…"
                val streams = cliente.streams()
                if (streams.isEmpty() || categorias.isEmpty()) throw IOException("El proveedor devolvió una lista vacía")
                runCatching { if (epgAntigua()) actualizarEpgSinBloqueo() }
                val informe = guardarProveedor(categorias, streams)
                ajustes.guardar(Ajustes.K.ULTIMA_SINCRONIZACION, System.currentTimeMillis())
                ajustes.guardar(Ajustes.K.ESTADO_SINCRONIZACION, "ok")
                ResumenSincronizacion(informe, null)
            } catch (e: Exception) {
                val cuenta = credenciales.leer()
                val msg = Redactor.redactar(e.message ?: e.javaClass.simpleName, cuenta)
                ajustes.guardar(Ajustes.K.ESTADO_SINCRONIZACION, "error: $msg")
                ResumenSincronizacion(null, msg)
            } finally {
                _progreso.value = null
            }
        }
    }

    /** Importa `live.json` y las categorías desde archivos (arranque sin red, sección 6.2.1). */
    suspend fun importarArchivos(streamsUri: Uri, categoriasUri: Uri): ResumenSincronizacion = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                _progreso.value = "Importando archivos…"
                val cr = context.contentResolver
                val categorias = cr.openInputStream(categoriasUri)!!.use { XtreamJson.categorias(it.readBytes().toString(Charsets.UTF_8)) }
                val streams = cr.openInputStream(streamsUri)!!.use { XtreamJson.streams(it) }
                if (streams.isEmpty() || categorias.isEmpty()) throw IOException("Archivos vacíos o con otro formato")
                ResumenSincronizacion(guardarProveedor(categorias, streams), null)
            } catch (e: Exception) {
                ResumenSincronizacion(null, e.message ?: e.javaClass.simpleName)
            } finally { _progreso.value = null }
        }
    }

    internal suspend fun guardarProveedor(categorias: List<Categoria>, streams: List<Stream>): InformeCambios {
        _progreso.value = "Comparando con la sincronización anterior…"
        val config = ajustes.configMotor()
        val antes = instantaneas(dao.categorias().map { Categoria(it.id, it.nombre, it.orden) }, dao.streams().map { it.aDominio() }, config::conserva)
        val despues = instantaneas(categorias, streams, config::conserva)
        val primera = antes.isEmpty()
        _progreso.value = "Guardando…"
        dao.reemplazarProveedor(
            categorias.map { CategoriaEntidad(it.id, it.nombre, it.orden) },
            streams.map { StreamEntidad(it.streamId, it.num, it.nombre, it.categoriaId, it.icono, it.epgChannelId, it.adulto, it.anadido) },
        )
        var informe = if (primera) InformeCambios(System.currentTimeMillis(), emptyList(), emptyList(), emptyList())
        else Comparador.comparar(antes, despues)
        recalcularInterno()
        informe = informe.copy(porRevisar = _casos.value.size)
        dao.insertarInforme(InformeEntidad(fecha = informe.fecha, anadidos = informe.anadidos.size, quitados = informe.quitados.size,
            cambiados = informe.cambiados.size, json = json.encodeToString(InformeCambios.serializer(), informe)))
        recalcularInterno()
        return informe
    }

    private fun instantaneas(cats: List<Categoria>, streams: List<Stream>, conserva: (String) -> Boolean): List<Instantanea> {
        val nombre = cats.associate { it.id to it.nombre }
        return streams.mapNotNull { s ->
            val g = nombre[s.categoriaId] ?: return@mapNotNull null
            if (!conserva(g) || s.nombre.trim().startsWith("#")) null else Instantanea(s.streamId, s.nombre, g, s.icono)
        }
    }

    // ---------- Guía ----------

    suspend fun urlGuia(): String = FuentesEpg.url(ajustes.leer(Ajustes.K.VARIANTE_EPG) ?: "guiatv_sincolor")

    private suspend fun epgAntigua(): Boolean =
        System.currentTimeMillis() - (ajustes.leer(Ajustes.K.ULTIMA_EPG) ?: 0L) > 4 * 3600_000L || dao.canalesEpg().isEmpty()

    suspend fun actualizarEpg(): String? = mutex.withLock { actualizarEpgSinBloqueo() }

    /** Descarga la guía en streaming y se detiene en la primera emisión. Si falla, se queda la copia guardada. */
    private suspend fun actualizarEpgSinBloqueo(): String? = withContext(Dispatchers.IO) {
        val url = urlGuia()
        var ultimo: Exception? = null
        for (intento in 0 until 3) {
            try {
                _progreso.value = "Descargando la guía…"
                val canales = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Guía: respuesta ${r.code}")
                    val flujo = r.body.byteStream().let { if (url.endsWith(".gz")) GZIPInputStream(it) else it }
                    val p = Xml.newPullParser()
                    p.setInput(flujo, "UTF-8")
                    LectorEpg.leer(p)
                }
                if (canales.isEmpty()) throw IOException("La guía no tiene canales")
                dao.reemplazarEpg(canales.map {
                    CanalEpgEntidad(it.id, it.posicion, json.encodeToString(ListSerializer(String.serializer()), it.nombres), it.icono)
                })
                ajustes.guardar(Ajustes.K.ULTIMA_EPG, System.currentTimeMillis())
                recalcularInterno()
                return@withContext null
            } catch (e: Exception) {
                ultimo = e
                kotlinx.coroutines.delay(2000L shl intento)
            } finally { _progreso.value = null }
        }
        "No se pudo descargar la guía (${ultimo?.message}); se usa la copia guardada"
    }

    suspend fun canalesEpg(): List<CanalEpg> = dao.canalesEpg().map {
        CanalEpg(it.id, runCatching { json.decodeFromString(ListSerializer(String.serializer()), it.nombresJson) }.getOrDefault(emptyList()), it.icono, it.posicion)
    }

    // ---------- Motor y decisiones ----------

    suspend fun recalcular() = mutex.withLock { withContext(Dispatchers.Default) { recalcularInterno() } }

    private suspend fun recalcularInterno() {
        val config = ajustes.configMotor()
        val categorias = dao.categorias().map { Categoria(it.id, it.nombre, it.orden) }
        val streams = dao.streams().map { it.aDominio() }
        val decisiones = dao.decisiones().map { it.aDominio() }
        val r = Motor(config).procesar(streams, categorias, canalesEpg(), decisiones)
        _resultado.value = r
        val ultimo = dao.ultimoInforme()?.let { runCatching { json.decodeFromString(InformeCambios.serializer(), it.json) }.getOrNull() }
        _casos.value = BandejaRevision.casos(r, decisiones, ultimo)
    }

    suspend fun decidir(d: Decision) {
        dao.guardarDecision(d.aEntidad())
        recalcular()
    }

    suspend fun deshacer(id: String) { dao.activarDecision(id, false); recalcular() }
    suspend fun rehacer(id: String) { dao.activarDecision(id, true); recalcular() }

    suspend fun decisiones(): List<Decision> = dao.decisiones().map { it.aDominio() }

    suspend fun exportarDecisiones(): String = Decisiones.exportar(decisiones())

    suspend fun importarDecisiones(texto: String): Int {
        val emp = _resultado.value?.emparejador
        val nuevas = Decisiones.importar(texto, emp)
        dao.guardarDecisiones(nuevas.map { it.aEntidad() })
        recalcular()
        return nuevas.size
    }

    suspend fun ultimoInforme(): InformeCambios? =
        dao.ultimoInforme()?.let { runCatching { json.decodeFromString(InformeCambios.serializer(), it.json) }.getOrNull() }

    suspend fun hayDatos(): Boolean = dao.numeroStreams() > 0

    suspend fun borrarDatos() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                bd.clearAllTables()
                credenciales.borrar()
                ajustes.borrarTodo()
                File(context.cacheDir, "exportar").deleteRecursively()
                _resultado.value = null
                _casos.value = emptyList()
            }
        }
    }

    companion object {
        const val UA_PREDETERMINADO = "VLC/3.0.20"
    }
}

fun StreamEntidad.aDominio() = Stream(streamId, num, nombre, categoriaId, icono, epgChannelId, adulto, anadido)

fun DecisionEntidad.aDominio() = Decision(
    id = id, tipo = runCatching { TipoDecision.valueOf(tipo) }.getOrDefault(TipoDecision.IGNORAR),
    streamId = streamId, nombre = nombre, clave = clave, grupo = grupo, ambito = ambito, valor = valor, fecha = fecha, activa = activa,
)

fun Decision.aEntidad() = DecisionEntidad(id, tipo.name, streamId, nombre, clave, grupo, ambito, valor, fecha, activa)
