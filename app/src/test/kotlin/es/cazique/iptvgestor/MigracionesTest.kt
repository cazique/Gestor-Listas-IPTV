package es.cazique.iptvgestor

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import es.cazique.iptvgestor.datos.bd.BaseDatos
import es.cazique.iptvgestor.datos.bd.Migraciones
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migración 1 → 2 sin pérdida de datos. Se crea a mano una base con el esquema 1 (el que generaba Room
 * en la versión 1) y datos, y se abre con la versión actual: Room valida el esquema resultante.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigracionesTest {
    private val esquema1 = listOf(
        "CREATE TABLE IF NOT EXISTS `stream` (`streamId` INTEGER NOT NULL, `num` INTEGER NOT NULL, `nombre` TEXT NOT NULL, `categoriaId` TEXT NOT NULL, `icono` TEXT NOT NULL, `epgChannelId` TEXT NOT NULL, `adulto` INTEGER NOT NULL, `anadido` INTEGER NOT NULL, PRIMARY KEY(`streamId`))",
        "CREATE INDEX IF NOT EXISTS `index_stream_categoriaId` ON `stream` (`categoriaId`)",
        "CREATE TABLE IF NOT EXISTS `categoria` (`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, `orden` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `canal_epg` (`id` TEXT NOT NULL, `posicion` INTEGER NOT NULL, `nombresJson` TEXT NOT NULL, `icono` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_canal_epg_posicion` ON `canal_epg` (`posicion`)",
        "CREATE TABLE IF NOT EXISTS `decision` (`id` TEXT NOT NULL, `tipo` TEXT NOT NULL, `streamId` INTEGER, `nombre` TEXT NOT NULL, `clave` TEXT NOT NULL, `grupo` TEXT NOT NULL, `ambito` TEXT NOT NULL, `valor` TEXT NOT NULL, `fecha` INTEGER NOT NULL, `activa` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_decision_streamId` ON `decision` (`streamId`)",
        "CREATE INDEX IF NOT EXISTS `index_decision_clave` ON `decision` (`clave`)",
        "CREATE INDEX IF NOT EXISTS `index_decision_fecha` ON `decision` (`fecha`)",
        "CREATE TABLE IF NOT EXISTS `informe` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fecha` INTEGER NOT NULL, `anadidos` INTEGER NOT NULL, `quitados` INTEGER NOT NULL, `cambiados` INTEGER NOT NULL, `json` TEXT NOT NULL)",
    )

    @Test fun de1a2ConservaDecisiones() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val nombre = "migracion.db"
        ctx.deleteDatabase(nombre)
        SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(nombre).apply { parentFile?.mkdirs() }, null).use { db ->
            esquema1.forEach(db::execSQL)
            db.execSQL("INSERT INTO decision VALUES ('d1','ASIGNAR_EPG',NULL,'','TDP','','','Teledeporte HD',1,1)")
            db.execSQL("INSERT INTO stream VALUES (7,1,'ES: LA 1','1','','',0,0)")
            db.version = 1
        }
        val bd = Room.databaseBuilder(ctx, BaseDatos::class.java, nombre).addMigrations(*Migraciones.TODAS).build()
        val dao = bd.dao()
        assertEquals(listOf("d1"), dao.decisiones().map { it.id })
        assertEquals(1, dao.numeroStreams())
        assertEquals(0, dao.resultadosEnlaces().size)
        bd.close()
    }
}
