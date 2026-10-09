package es.cazique.iptvgestor.datos.bd

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stream", indices = [Index("categoriaId")])
data class StreamEntidad(
    @PrimaryKey val streamId: Long,
    val num: Long,
    val nombre: String,
    val categoriaId: String,
    val icono: String,
    val epgChannelId: String,
    val adulto: Boolean,
    val anadido: Long,
)

@Entity(tableName = "categoria")
data class CategoriaEntidad(@PrimaryKey val id: String, val nombre: String, val orden: Int)

@Entity(tableName = "canal_epg", indices = [Index("posicion")])
data class CanalEpgEntidad(@PrimaryKey val id: String, val posicion: Int, val nombresJson: String, val icono: String)

/** Decisiones manuales: nunca se borran (deshacer = `activa = false`), así queda el historial. */
@Entity(tableName = "decision", indices = [Index("streamId"), Index("clave"), Index("fecha")])
data class DecisionEntidad(
    @PrimaryKey val id: String,
    val tipo: String,
    val streamId: Long?,
    val nombre: String,
    val clave: String,
    val grupo: String,
    val ambito: String,
    val valor: String,
    val fecha: Long,
    val activa: Boolean,
)

@Entity(tableName = "informe")
data class InformeEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long,
    val anadidos: Int,
    val quitados: Int,
    val cambiados: Int,
    val json: String,
)

/** Resultado de la comprobación de enlaces por variante (sin URL). Añadida en el esquema 2. */
@Entity(tableName = "resultado_enlace")
data class ResultadoEnlaceEntidad(
    @PrimaryKey val streamId: Long,
    val estado: String,
    val msPrimerDato: Long?,
    val kbps: Long?,
    val bytes: Long,
    val fecha: Long,
    val detalle: String,
)

@Dao
interface DaoDatos {
    @Upsert suspend fun guardarResultado(r: ResultadoEnlaceEntidad)
    @Query("SELECT * FROM resultado_enlace") suspend fun resultadosEnlaces(): List<ResultadoEnlaceEntidad>
    @Query("SELECT * FROM resultado_enlace") fun resultadosEnlacesFlujo(): Flow<List<ResultadoEnlaceEntidad>>
    @Query("DELETE FROM resultado_enlace") suspend fun borrarResultados()

    @Query("SELECT * FROM stream") suspend fun streams(): List<StreamEntidad>
    @Query("SELECT * FROM categoria ORDER BY orden") suspend fun categorias(): List<CategoriaEntidad>
    @Query("SELECT * FROM canal_epg ORDER BY posicion") suspend fun canalesEpg(): List<CanalEpgEntidad>
    @Query("SELECT COUNT(*) FROM stream") suspend fun numeroStreams(): Int

    @Query("DELETE FROM stream") suspend fun borrarStreams()
    @Query("DELETE FROM categoria") suspend fun borrarCategorias()
    @Query("DELETE FROM canal_epg") suspend fun borrarEpg()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertarStreams(s: List<StreamEntidad>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertarCategorias(c: List<CategoriaEntidad>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertarEpg(c: List<CanalEpgEntidad>)

    /** Todo o nada (sección 10.5). */
    @Transaction
    suspend fun reemplazarProveedor(categorias: List<CategoriaEntidad>, streams: List<StreamEntidad>) {
        borrarCategorias(); borrarStreams()
        insertarCategorias(categorias)
        streams.chunked(2000).forEach { insertarStreams(it) }
    }

    @Transaction
    suspend fun reemplazarEpg(canales: List<CanalEpgEntidad>) { borrarEpg(); insertarEpg(canales) }

    @Query("SELECT * FROM decision ORDER BY fecha") suspend fun decisiones(): List<DecisionEntidad>
    @Query("SELECT * FROM decision ORDER BY fecha DESC") fun decisionesFlujo(): Flow<List<DecisionEntidad>>
    @Upsert suspend fun guardarDecision(d: DecisionEntidad)
    @Upsert suspend fun guardarDecisiones(d: List<DecisionEntidad>)
    @Query("UPDATE decision SET activa = :activa WHERE id = :id") suspend fun activarDecision(id: String, activa: Boolean)

    @Insert suspend fun insertarInforme(i: InformeEntidad): Long
    @Query("SELECT * FROM informe ORDER BY fecha DESC LIMIT 1") suspend fun ultimoInforme(): InformeEntidad?
    @Query("SELECT * FROM informe ORDER BY fecha DESC LIMIT 30") fun informes(): Flow<List<InformeEntidad>>

    @Query("DELETE FROM decision") suspend fun borrarDecisiones()
    @Query("DELETE FROM informe") suspend fun borrarInformes()
}

/**
 * Base de datos. Prohibidas las migraciones destructivas: cada cambio de esquema
 * lleva su `Migration` en [Migraciones] y su prueba (MigracionesTest).
 */
@Database(
    entities = [
        StreamEntidad::class, CategoriaEntidad::class, CanalEpgEntidad::class, DecisionEntidad::class, InformeEntidad::class,
        ResultadoEnlaceEntidad::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun dao(): DaoDatos

    companion object {
        const val NOMBRE = "iptvgestor.db"

        fun crear(context: Context): BaseDatos =
            Room.databaseBuilder(context, BaseDatos::class.java, NOMBRE)
                .addMigrations(*Migraciones.TODAS)
                .build()
    }
}

object Migraciones {
    /** 1 → 2: resultados de la comprobación de enlaces (Fase 3). No toca las tablas existentes. */
    val DE_1_A_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `resultado_enlace` (`streamId` INTEGER NOT NULL, `estado` TEXT NOT NULL, " +
                    "`msPrimerDato` INTEGER, `kbps` INTEGER, `bytes` INTEGER NOT NULL, `fecha` INTEGER NOT NULL, " +
                    "`detalle` TEXT NOT NULL, PRIMARY KEY(`streamId`))"
            )
        }
    }

    val TODAS: Array<androidx.room.migration.Migration> = arrayOf(DE_1_A_2)
}
