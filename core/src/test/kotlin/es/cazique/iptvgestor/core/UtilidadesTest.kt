package es.cazique.iptvgestor.core

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class UtilidadesTest {
    private val server = MockWebServer()
    @After fun fin() = server.close()

    private fun estado(activas: Int) = """{"user_info":{"status":"Active","max_connections":"1","active_cons":"$activas","auth":1},"server_info":{"https_port":"443"}}"""

    @Test fun pruebaDeUnoEnUnoYParaSiHayConexionAjena() {
        val llamadasApi = AtomicInteger()
        val enCurso = AtomicInteger()
        var maxSimultaneas = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ruta = request.target
                if (ruta.startsWith("/player_api.php")) {
                    val n = llamadasApi.incrementAndGet()
                    return MockResponse.Builder().body(estado(if (n >= 3) 1 else 0)).build()
                }
                val actual = enCurso.incrementAndGet(); maxSimultaneas = maxOf(maxSimultaneas, actual)
                enCurso.decrementAndGet()
                return if (ruta.contains("/2.ts")) MockResponse.Builder().code(404).build()
                else MockResponse.Builder().body(okio.Buffer().write(ByteArray(300_000))).build()
            }
        }
        server.start()
        val cuenta = Cuenta(server.url("/").toString().trimEnd('/'), "u", "p")
        val http = OkHttpClient()
        val c = ComprobadorEnlaces(http, ClienteXtream(http, cuenta), cuenta, "VLC/3.0.20",
            OpcionesComprobacion(segundosMuestra = 1, esperaEntrePruebasMs = 0), dormir = {})
        val (res, parada) = c.comprobar(listOf(1, 2, 3, 4))
        assertEquals(2, res.size)               // la tercera consulta ve active_cons = 1 y se detiene
        assertEquals(Parada.ConexionAjena, parada)
        assertEquals(EstadoEnlace.FUNCIONA, res[0].estado)
        assertTrue((res[0].kbps ?: 0) > 0)
        assertEquals(EstadoEnlace.FALLA, res[1].estado)
        assertEquals(1, maxSimultaneas)
        assertFalse(res.toString().contains("/live/"))  // sin URL en los resultados
    }

    @Test fun pinConHashYSal() {
        val h1 = Pin.hash("1234"); val h2 = Pin.hash("1234")
        assertTrue(h1 != h2)
        assertTrue(Pin.verificar("1234", h1))
        assertFalse(Pin.verificar("1235", h1))
        assertFalse(h1.contains("1234"))
    }

    @Test fun subidaPorPut() {
        server.enqueue(MockResponse.Builder().code(201).build())
        server.start()
        Subidor(OkHttpClient()).subir(server.url("/dav").toString(), "lista.m3u", "#EXTM3U\n", "jorgeprueba", "clave")
        val r = server.takeRequest()
        assertEquals("PUT", r.method)
        assertEquals("/dav/lista.m3u", r.target)
        assertTrue(r.headers["Authorization"]!!.startsWith("Basic "))
    }
}
