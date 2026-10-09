package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** Acciones manuales de la sección 5.1. */
@Serializable
enum class TipoDecision(val descripcion: String, val deVariante: Boolean) {
    UNIR("Unir con otro canal", true),
    SEPARAR("Separar variante", true),
    CAMBIAR_PAQUETE("Cambiar de paquete", true),
    PREFERIDA("Variante preferida", true),
    OCULTAR_VARIANTE("Ocultar variante", true),
    OCULTAR_CANAL("Ocultar canal", false),
    ASIGNAR_EPG("Asignar guía", false),
    QUITAR_EPG("Quitar guía", false),
    ICONO("Cambiar icono", false),
    NO_DUPLICADO("No es duplicado", false),
    IGNORAR("Ignorar en revisión", false),
    OCULTAR_PELICULA("Ocultar película", false),
}

/**
 * Decisión manual. Se guarda con dos identidades (sección 5.3):
 * - variante: `streamId` y, como respaldo, nombre normalizado + grupo de origen (y la clave);
 * - canal lógico: la `clave` (y el `ambito`, paquete o grupo, cuando importa).
 * Nunca se borran: deshacer marca `activa = false` y queda en el historial.
 */
@Serializable
data class Decision(
    val id: String = UUID.randomUUID().toString(),
    val tipo: TipoDecision,
    val streamId: Long? = null,
    val nombre: String = "",
    val clave: String = "",
    val grupo: String = "",
    val ambito: String = "",
    val valor: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val activa: Boolean = true,
)

@Serializable
data class CopiaDecisiones(val formato: Int = 1, val app: String = "iptvgestor", val decisiones: List<Decision>)

object Decisiones {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun exportar(decisiones: List<Decision>): String = json.encodeToString(CopiaDecisiones.serializer(), CopiaDecisiones(decisiones = decisiones))

    /** Importa el JSON propio o el formato simple `NOMBRE = NOMBRE EN LA GUÍA` (como alias de guía). */
    fun importar(texto: String, epg: Emparejador? = null): List<Decision> {
        val t = texto.trim()
        if (t.startsWith("{")) return json.decodeFromString(CopiaDecisiones.serializer(), t).decisiones
        return AliasEpg.leerAliasUsuario(t).mapNotNull { (origen, destino) ->
            val canal = epg?.let { e -> e.epg.firstOrNull { c -> c.nombres.any { Normalizacion.clave(it) == destino } } }
            Decision(tipo = TipoDecision.ASIGNAR_EPG, clave = origen, valor = canal?.id ?: destino)
        }
    }

    /** La última decisión activa de cada tipo y destino es la que manda. */
    fun vigentes(todas: List<Decision>): List<Decision> =
        todas.filter { it.activa }
            .sortedBy { it.fecha }
            .associateBy { Triple(it.tipo, it.streamId ?: it.nombre, it.clave + "|" + it.ambito) }
            .values.toList()
}

/** Resuelve qué decisiones de variante se aplican a cada stream (por id o, si cambió, por nombre y grupo). */
internal class IndiceDecisiones(decisiones: List<Decision>) {
    private val vigentes = Decisiones.vigentes(decisiones)
    private val variante = vigentes.filter { it.tipo.deVariante }
    private val porId = variante.filter { it.streamId != null }.groupBy { it.streamId!! }
    private val porNombre = variante.groupBy { it.nombre + "\u0000" + it.grupo }
    val canal: Map<Pair<TipoDecision, String>, Decision> = vigentes.filter { !it.tipo.deVariante }
        .associateBy { it.tipo to it.clave }
    val usadas = HashSet<String>()

    fun deVariante(s: Stream, grupo: String): Map<TipoDecision, Decision> {
        val porIdActual = porId[s.streamId].orEmpty()
        val lista = porIdActual.ifEmpty { porNombre[Normalizacion.nombreNormalizado(s.nombre) + "\u0000" + grupo].orEmpty() }
        lista.forEach { usadas.add(it.id) }
        return lista.associateBy { it.tipo }
    }

    fun deCanal(tipo: TipoDecision, clave: String): Decision? = canal[tipo to clave]?.also { usadas.add(it.id) }

    fun huerfanas(): List<Decision> = vigentes.filter { it.tipo.deVariante && it.id !in usadas }
}
