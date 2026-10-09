package es.cazique.iptvgestor.core

/** Tipos de caso de la bandeja "Por revisar", en orden de importancia (sección 5.2). */
enum class TipoCaso(val titulo: String) {
    SIN_GUIA("Sin guía"),
    CONFIANZA_BAJA("Emparejado dudoso"),
    POSIBLE_DUPLICADO("Posible duplicado"),
    NUEVO("Canal nuevo"),
    DESAPARECIDO("Desaparecido con decisiones"),
}

data class Sugerencia(val texto: String, val epg: CanalEpg? = null, val clave: String? = null, val confianza: Double)

data class CasoRevision(
    val id: String,
    val tipo: TipoCaso,
    val clave: String,
    val ambito: String,
    val descripcion: String,
    val sugerencias: List<Sugerencia>,
    val canal: CanalLogico? = null,
    val decision: Decision? = null,
)

object BandejaRevision {
    const val UMBRAL_CONFIANZA = 0.95
    const val UMBRAL_DUPLICADO = 0.9

    fun casos(
        resultado: ResultadoMotor,
        decisiones: List<Decision>,
        ultimoInforme: InformeCambios? = null,
        maxDuplicadosPorAmbito: Int = 2000,
    ): List<CasoRevision> {
        val vigentes = Decisiones.vigentes(decisiones)
        val ignorados = vigentes.filter { it.tipo == TipoDecision.IGNORAR }.map { it.valor + "|" + it.clave }.toSet()
        val noDuplicados = vigentes.filter { it.tipo == TipoDecision.NO_DUPLICADO }.map { it.clave + "|" + it.valor }.toSet()
        val casos = ArrayList<CasoRevision>()
        val emp = resultado.emparejador
        val visibles = resultado.canales.filter { !it.oculto && it.tipoAmbito != TipoAmbito.EXTRA }
        // Los canales de paquetes primero: son los que se reparten en capas.
        val ordenados = visibles.sortedBy { if (it.tipoAmbito == TipoAmbito.PAQUETE) 0 else 1 }

        val vistosSinGuia = HashSet<String>()
        for (c in ordenados) {
            if (c.emparejado != null || !vistosSinGuia.add(c.clave)) continue
            if ("${TipoCaso.SIN_GUIA}|${c.clave}" in ignorados) continue
            val sug = emp.candidatas(c.clave).map { (e, p) -> Sugerencia(e.id, e, confianza = p) }
            casos.add(CasoRevision("${TipoCaso.SIN_GUIA}|${c.clave}", TipoCaso.SIN_GUIA, c.clave, c.ambito,
                "${c.clave} (${c.ambito}) no tiene guía", sug, c))
        }
        val vistosDudosos = HashSet<String>()
        for (c in ordenados) {
            val e = c.emparejado ?: continue
            if (e.confianza >= UMBRAL_CONFIANZA || !vistosDudosos.add(c.clave)) continue
            if ("${TipoCaso.CONFIANZA_BAJA}|${c.clave}" in ignorados) continue
            val sug = listOf(Sugerencia(e.canal.id, e.canal, confianza = e.confianza)) +
                emp.candidatas(c.clave).filter { it.first.id != e.canal.id }.map { (x, p) -> Sugerencia(x.id, x, confianza = p) }
            casos.add(CasoRevision("${TipoCaso.CONFIANZA_BAJA}|${c.clave}", TipoCaso.CONFIANZA_BAJA, c.clave, c.ambito,
                "${c.clave} → ${e.canal.id} (${(e.confianza * 100).toInt()} %)", sug, c))
        }
        for ((ambito, cs) in visibles.groupBy { it.ambito }) {
            if (cs.size > maxDuplicadosPorAmbito) continue
            val compactas = cs.map { Normalizacion.compacto(it.clave) }
            for (i in cs.indices) for (j in i + 1 until cs.size) {
                val a = cs[i]; val b = cs[j]
                if (Normalizacion.numeros(a.clave) != Normalizacion.numeros(b.clave)) continue
                val r = Difflib.ratio(compactas[i], compactas[j])
                if (r < UMBRAL_DUPLICADO) continue
                if ("${a.clave}|${b.clave}" in noDuplicados || "${b.clave}|${a.clave}" in noDuplicados) continue
                val id = "${TipoCaso.POSIBLE_DUPLICADO}|$ambito|${a.clave}|${b.clave}"
                if ("${TipoCaso.POSIBLE_DUPLICADO}|${a.clave}" in ignorados) continue
                casos.add(CasoRevision(id, TipoCaso.POSIBLE_DUPLICADO, b.clave, ambito,
                    "¿${b.clave} es el mismo canal que ${a.clave}? ($ambito)",
                    listOf(Sugerencia(a.clave, clave = a.clave, confianza = r)), b))
            }
        }
        ultimoInforme?.anadidos?.forEach { n ->
            val clave = Normalizacion.clave(n.nombre)
            if ("${TipoCaso.NUEVO}|${n.streamId}" in ignorados) return@forEach
            val canal = resultado.canales.firstOrNull { c -> c.variantes.any { it.stream.streamId == n.streamId } }
            casos.add(CasoRevision("${TipoCaso.NUEVO}|${n.streamId}", TipoCaso.NUEVO, clave, n.grupo,
                "Nuevo: ${n.nombre} [${n.grupo}]", emptyList(), canal))
        }
        for (d in resultado.decisionesHuerfanas) {
            casos.add(CasoRevision("${TipoCaso.DESAPARECIDO}|${d.id}", TipoCaso.DESAPARECIDO, d.clave, d.grupo,
                "Ya no existe: ${d.nombre.ifEmpty { d.clave }} [${d.grupo}] · ${d.tipo.descripcion}", emptyList(), decision = d))
        }
        return casos
    }
}
