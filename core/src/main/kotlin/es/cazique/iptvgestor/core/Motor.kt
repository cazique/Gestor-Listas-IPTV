package es.cazique.iptvgestor.core

/** Una copia concreta (stream) de un canal lógico. */
data class Variante(
    val stream: Stream,
    val grupo: String,
    val claveOriginal: String,
    val puntos: Int,
    val respaldo: Boolean,
    val preferida: Boolean = false,
    val oculta: Boolean = false,
    val manual: Boolean = false,
)

enum class TipoAmbito { PAQUETE, GRUPO, EXTRA }

/** Canal lógico dentro de un ámbito (un paquete o un grupo que pasa tal cual). */
data class CanalLogico(
    val ambito: String,
    val tipoAmbito: TipoAmbito,
    val clave: String,
    val variantes: List<Variante>,
    val emparejado: Emparejado?,
    val icono: String,
    val oculto: Boolean,
    val manual: Boolean,
    val adulto: Boolean,
) {
    val tvgId: String get() = emparejado?.canal?.id ?: ""
    val visibles: List<Variante> get() = if (oculto) emptyList() else variantes.filterNot { it.oculta }
}

/** Una línea de la lista final. */
data class EntradaLista(
    val grupoSalida: String,
    val canal: CanalLogico,
    val variante: Variante,
    val capa: Int?,
    val nombreMostrado: String,
)

data class CifrasPaquete(val nombre: String, val canales: Int, val entradas: Int, val capas: List<Int>, val conGuia: Int)

data class Cifras(
    val canalesOrigen: Int,
    val gruposOrigen: Int,
    val gruposConservados: Int,
    val entradasConservadas: Int,
    val separadores: Int,
    val paquetes: List<CifrasPaquete>,
    val entradasLista: Int,
    val gruposLista: Int,
    val canalesLogicos: Int,
    val conGuia: Int,
    val sinGuia: Int,
)

data class ResultadoMotor(
    val canales: List<CanalLogico>,
    val lista: List<EntradaLista>,
    val cifras: Cifras,
    val decisionesHuerfanas: List<Decision>,
    val emparejador: Emparejador,
)

/**
 * Motor de la sección 4: filtro, claves, paquetes, capas, guía y lista final.
 * Kotlin puro y determinista: con los mismos datos siempre da la misma lista.
 */
class Motor(private val config: ConfigMotor = ConfigMotor()) {

    fun procesar(
        streams: List<Stream>,
        categorias: List<Categoria>,
        epg: List<CanalEpg>,
        decisiones: List<Decision> = emptyList(),
        /** kbps medidos por stream (comprobación de enlaces); solo se usan si `ordenarPorTasa`. */
        tasas: Map<Long, Long> = emptyMap(),
    ): ResultadoMotor {
        val nombreGrupo = categorias.associate { it.id to it.nombre }
        val ordenGrupo = categorias.associate { it.nombre to it.orden }
        val emparejador = Emparejador(epg, config.alias, config.aliasUsuario)
        val dec = IndiceDecisiones(decisiones)
        val paquetes = config.paquetes.filter { it.activo }.map { it.nombre }

        // 1. Filtro y asignación de ámbito (con las decisiones de variante ya aplicadas).
        data class Pre(val ambito: String, val tipo: TipoAmbito, val clave: String, val v: Variante)
        val pre = ArrayList<Pre>()
        var separadores = 0
        var conservadas = 0
        val gruposConservados = HashSet<String>()
        for (s in streams) {
            val grupo = nombreGrupo[s.categoriaId] ?: continue
            if (!config.conserva(grupo)) continue
            conservadas++
            gruposConservados.add(grupo)
            if (Normalizacion.esSeparador(s.nombre)) { separadores++; continue }
            val d = dec.deVariante(s, grupo)
            val claveOriginal = Normalizacion.clave(s.nombre)
            var clave = claveOriginal
            d[TipoDecision.UNIR]?.let { clave = it.valor }
            d[TipoDecision.SEPARAR]?.let { clave = it.valor.ifEmpty { "$claveOriginal [${s.streamId}]" } }
            var (ambito, tipo) = when {
                config.esExtra(grupo) -> grupo to TipoAmbito.EXTRA
                else -> (config.paqueteDe(grupo)?.nombre?.let { it to TipoAmbito.PAQUETE }) ?: (grupo to TipoAmbito.GRUPO)
            }
            d[TipoDecision.CAMBIAR_PAQUETE]?.let {
                if (it.valor.isEmpty()) { ambito = grupo; tipo = TipoAmbito.GRUPO }
                else if (it.valor in paquetes) { ambito = it.valor; tipo = TipoAmbito.PAQUETE }
            }
            if (d[TipoDecision.UNIR] != null && d[TipoDecision.UNIR]!!.ambito.isNotEmpty()) {
                val destino = d[TipoDecision.UNIR]!!.ambito
                ambito = destino
                tipo = if (destino in paquetes) TipoAmbito.PAQUETE else TipoAmbito.GRUPO
            }
            val v = Variante(
                stream = s, grupo = grupo, claveOriginal = claveOriginal,
                puntos = Normalizacion.puntos(s.nombre, config.preferencia),
                respaldo = Normalizacion.esRespaldo(s.nombre, grupo),
                preferida = d.containsKey(TipoDecision.PREFERIDA),
                oculta = d.containsKey(TipoDecision.OCULTAR_VARIANTE),
                manual = d.isNotEmpty(),
            )
            pre.add(Pre(ambito, tipo, clave, v))
        }

        // 2. Canales lógicos por (ámbito, clave), en orden de primera aparición.
        val agrupado = LinkedHashMap<Pair<String, String>, MutableList<Pre>>()
        for (p in pre) agrupado.getOrPut(p.ambito to p.clave) { ArrayList() }.add(p)

        val cacheEpg = HashMap<String, Emparejado?>()
        val canales = agrupado.map { (k, ps) ->
            val (ambito, clave) = k
            val ordenVariantes = if (config.ordenarPorTasa && tasas.isNotEmpty()) {
                compareBy<Variante>({ !it.preferida }, { it.puntos >= 10 }, { -(tasas[it.stream.streamId] ?: -1L) }, { it.puntos }, { it.stream.num })
            } else {
                compareBy<Variante>({ !it.preferida }, { it.puntos }, { if (config.respaldoAlFinal && it.respaldo) 1 else 0 }, { it.stream.num })
            }
            val variantes = ps.map { it.v }.sortedWith(ordenVariantes)
            val quitar = dec.deCanal(TipoDecision.QUITAR_EPG, clave)
            val asignar = dec.deCanal(TipoDecision.ASIGNAR_EPG, clave)
            val emp = when {
                asignar != null && (quitar == null || asignar.fecha > quitar.fecha) ->
                    emparejador.canalPorId(asignar.valor)?.let { Emparejado(it, MetodoEmparejado.MANUAL, 1.0) }
                quitar != null -> null
                else -> cacheEpg.getOrPut(clave) { emparejador.buscar(clave) }
            }
            val iconoManual = dec.deCanal(TipoDecision.ICONO, clave)?.valor
            val oculto = dec.deCanal(TipoDecision.OCULTAR_CANAL, clave) != null
            CanalLogico(
                ambito = ambito, tipoAmbito = ps.first().tipo, clave = clave, variantes = variantes, emparejado = emp,
                icono = iconoManual?.takeIf { it.isNotBlank() } ?: emp?.canal?.icono?.takeIf { it.isNotBlank() }
                    ?: variantes.firstOrNull { it.stream.icono.isNotBlank() }?.stream?.icono ?: "",
                oculto = oculto,
                manual = asignar != null || quitar != null || iconoManual != null || oculto || variantes.any { it.manual },
                adulto = ps.first().tipo == TipoAmbito.EXTRA || variantes.any { it.stream.adulto },
            )
        }

        // 3. Lista final: capas de cada paquete, resto de grupos y extras.
        val lista = ArrayList<EntradaLista>()
        val ordenGuia = compareBy<CanalLogico>({ it.emparejado == null }, { it.emparejado?.canal?.posicion ?: 0 })
        val porAmbito = canales.groupBy { it.ambito }
        val cifrasPaquetes = ArrayList<CifrasPaquete>()
        for (pq in paquetes) {
            val cs = porAmbito[pq].orEmpty().sortedWith(ordenGuia) // sortedWith es estable: los sin guía quedan en su orden
            val capas = ArrayList<Int>()
            for (capa in 1..config.maxCapas) {
                var n = 0
                for (c in cs) {
                    val v = c.visibles.getOrNull(capa - 1) ?: continue
                    lista.add(EntradaLista("$pq $capa", c, v, capa, c.clave)); n++
                }
                if (n > 0) capas.add(n)
            }
            cifrasPaquetes.add(CifrasPaquete(pq, cs.size, cs.sumOf { it.variantes.size }, capas, cs.count { it.emparejado != null }))
        }
        val restantes = porAmbito.filterKeys { it !in paquetes }
        val ordenAmbitos = restantes.keys.sortedWith(
            compareBy<String>({ restantes.getValue(it).first().tipoAmbito == TipoAmbito.EXTRA }, { ordenGrupo[it] ?: Int.MAX_VALUE })
        )
        for (ambito in ordenAmbitos) {
            val cs = restantes.getValue(ambito).sortedWith(ordenGuia)
            if (config.capasEnRestoGrupos && cs.first().tipoAmbito == TipoAmbito.GRUPO) {
                for (capa in 1..config.maxCapas) for (c in cs) {
                    val v = c.visibles.getOrNull(capa - 1) ?: continue
                    lista.add(EntradaLista("$ambito $capa", c, v, capa, c.clave))
                }
            } else {
                for (c in cs) for (v in c.visibles) lista.add(EntradaLista(ambito, c, v, null, v.stream.nombre.trim()))
            }
        }

        val conGuia = canales.count { it.emparejado != null }
        val cifras = Cifras(
            canalesOrigen = streams.size,
            gruposOrigen = categorias.size,
            gruposConservados = gruposConservados.size,
            entradasConservadas = conservadas,
            separadores = separadores,
            paquetes = cifrasPaquetes,
            entradasLista = lista.size,
            gruposLista = lista.map { it.grupoSalida }.distinct().size,
            canalesLogicos = canales.size,
            conGuia = conGuia,
            sinGuia = canales.size - conGuia,
        )
        return ResultadoMotor(canales, lista, cifras, dec.huerfanas(), emparejador)
    }
}
