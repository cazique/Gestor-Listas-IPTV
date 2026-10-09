package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable

@Serializable
enum class TipoRegla { CONTIENE, EMPIEZA_POR, GRUPO_EXACTO }

/** Regla sobre el nombre del grupo del proveedor (comparación en mayúsculas). */
@Serializable
data class Regla(val tipo: TipoRegla, val texto: String) {
    fun cumple(grupo: String): Boolean {
        val g = grupo.uppercase()
        val t = texto.uppercase()
        return when (tipo) {
            TipoRegla.CONTIENE -> t in g
            TipoRegla.EMPIEZA_POR -> g.startsWith(t)
            TipoRegla.GRUPO_EXACTO -> g == t
        }
    }
}

/** Paquete (operadora): grupos del proveedor cuyos canales se reparten en capas. */
@Serializable
data class Paquete(val nombre: String, val reglas: List<Regla>, val activo: Boolean = true) {
    fun incluye(grupo: String): Boolean = activo && reglas.any { it.cumple(grupo) }

    companion object {
        val PREDETERMINADOS = listOf(
            Paquete("M+", listOf(Regla(TipoRegla.CONTIENE, "MOVISTAR"), Regla(TipoRegla.EMPIEZA_POR, "ES| M+"))),
            Paquete("Vodafone", listOf(Regla(TipoRegla.CONTIENE, "VODAFONE"))),
            Paquete("Orange", listOf(Regla(TipoRegla.CONTIENE, "ORANGE"))),
        )
    }
}

/** Ajustes del motor (sección 4). Todo editable desde "Paquetes y filtros". */
@Serializable
data class ConfigMotor(
    val prefijosGrupo: List<String> = listOf("ES|"),
    val extras: List<String> = listOf("FOR ADULTS"),
    val ppvTodos: Boolean = false,
    val paquetes: List<Paquete> = Paquete.PREDETERMINADOS,
    val maxCapas: Int = 5,
    val preferencia: List<String> = Normalizacion.PERFIL_NORMAL,
    val respaldoAlFinal: Boolean = true,
    val capasEnRestoGrupos: Boolean = false,
    val alias: AliasEpg = AliasEpg(),
    val aliasUsuario: Map<String, String> = emptyMap(),
) {
    /** Grupos que entran por prefijo (los de los paquetes deben ser de estos). */
    fun esGrupoPrincipal(grupo: String): Boolean = prefijosGrupo.any { grupo.uppercase().startsWith(it.uppercase()) }

    fun esExtra(grupo: String): Boolean = extras.any { it.equals(grupo.trim(), ignoreCase = true) }

    fun conserva(grupo: String): Boolean =
        esGrupoPrincipal(grupo) || esExtra(grupo) || (ppvTodos && "PPV" in grupo.uppercase())

    fun paqueteDe(grupo: String): Paquete? =
        if (esGrupoPrincipal(grupo)) paquetes.firstOrNull { it.incluye(grupo) } else null
}
