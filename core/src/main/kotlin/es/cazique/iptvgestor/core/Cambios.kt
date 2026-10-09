package es.cazique.iptvgestor.core

import kotlinx.serialization.Serializable

/** Lo que se recuerda de cada canal conservado para comparar sincronizaciones (sección 10). */
@Serializable
data class Instantanea(val streamId: Long, val nombre: String, val grupo: String, val icono: String) {
    val identidad: String get() = Normalizacion.nombreNormalizado(nombre) + "\u0000" + grupo
}

@Serializable
data class Cambio(val antes: Instantanea, val despues: Instantanea, val que: List<String>)

@Serializable
data class InformeCambios(
    val fecha: Long,
    val anadidos: List<Instantanea>,
    val quitados: List<Instantanea>,
    val cambiados: List<Cambio>,
    val porRevisar: Int = 0,
) {
    val vacio: Boolean get() = anadidos.isEmpty() && quitados.isEmpty() && cambiados.isEmpty()

    fun comoTexto(maximo: Int = 200): String = buildString {
        appendLine("Informe de cambios")
        appendLine("Añadidos: ${anadidos.size} · Quitados: ${quitados.size} · Cambiados: ${cambiados.size} · Por revisar: $porRevisar")
        fun bloque(titulo: String, filas: List<String>) {
            if (filas.isEmpty()) return
            appendLine()
            appendLine("$titulo (${filas.size})")
            filas.take(maximo).forEach { appendLine("  $it") }
            if (filas.size > maximo) appendLine("  … y ${filas.size - maximo} más")
        }
        bloque("Añadidos", anadidos.map { "${it.nombre} [${it.grupo}]" })
        bloque("Quitados", quitados.map { "${it.nombre} [${it.grupo}]" })
        bloque("Cambiados", cambiados.map { "${it.antes.nombre} → ${it.despues.nombre} (${it.que.joinToString()})" })
    }
}

object Comparador {
    /** Compara por `stream_id` y, como respaldo, por nombre normalizado y grupo (por si el proveedor cambió el id). */
    fun comparar(antes: List<Instantanea>, despues: List<Instantanea>, fecha: Long = System.currentTimeMillis()): InformeCambios {
        val a = antes.associateBy { it.streamId }
        val d = despues.associateBy { it.streamId }
        val cambiados = ArrayList<Cambio>()
        val sinPareja = despues.filter { it.streamId !in a }.toMutableList()
        val perdidos = antes.filter { it.streamId !in d }.toMutableList()
        for ((id, nuevo) in d) {
            val viejo = a[id] ?: continue
            val que = buildList {
                if (viejo.nombre != nuevo.nombre) add("nombre")
                if (viejo.grupo != nuevo.grupo) add("grupo")
                if (viejo.icono != nuevo.icono) add("icono")
            }
            if (que.isNotEmpty()) cambiados.add(Cambio(viejo, nuevo, que))
        }
        // Respaldo por identidad: mismo nombre y grupo con otro stream_id.
        val perdidosPorIdentidad = perdidos.groupBy { it.identidad }.mapValues { it.value.toMutableList() }
        val anadidos = ArrayList<Instantanea>()
        for (n in sinPareja) {
            val viejo = perdidosPorIdentidad[n.identidad]?.removeFirstOrNull()
            if (viejo != null) {
                perdidos.remove(viejo)
                cambiados.add(Cambio(viejo, n, listOf("stream_id") + if (viejo.icono != n.icono) listOf("icono") else emptyList()))
            } else anadidos.add(n)
        }
        return InformeCambios(fecha, anadidos, perdidos, cambiados)
    }
}
