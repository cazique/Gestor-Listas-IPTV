package es.cazique.iptvgestor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import es.cazique.iptvgestor.core.Decision
import es.cazique.iptvgestor.core.TipoDecision
import es.cazique.iptvgestor.datos.aDominio
import es.cazique.iptvgestor.datos.aEntidad
import es.cazique.iptvgestor.datos.bd.BaseDatos
import es.cazique.iptvgestor.datos.bd.Migraciones
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las decisiones manuales sobreviven al cierre y reapertura de la base de datos (como tras una actualización). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenciaTest {
    @Test fun decisionesSobrevivenAReabrir() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val nombre = "prueba_persistencia.db"
        ctx.deleteDatabase(nombre)
        val d = Decision(tipo = TipoDecision.ASIGNAR_EPG, clave = "TDP", valor = "Teledeporte HD", fecha = 1)
        Room.databaseBuilder(ctx, BaseDatos::class.java, nombre).addMigrations(*Migraciones.TODAS).build().apply {
            dao().guardarDecision(d.aEntidad()); close()
        }
        val bd = Room.databaseBuilder(ctx, BaseDatos::class.java, nombre).addMigrations(*Migraciones.TODAS).build()
        assertEquals(listOf(d), bd.dao().decisiones().map { it.aDominio() })
        bd.close()
    }
}
