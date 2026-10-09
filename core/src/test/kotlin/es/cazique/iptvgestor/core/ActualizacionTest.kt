package es.cazique.iptvgestor.core

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/** Prueba de integración de la lógica de actualización con un servidor simulado (sección 6.4.5). */
class ActualizacionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val http = OkHttpClient()
    private val apk = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private lateinit var politica: PoliticaRed

    @Before fun inicio() {
        server.start()
        politica = PoliticaRed(hosts = setOf(server.hostName), soloHttps = false)
    }

    @After fun fin() = server.close()

    private fun release(versionCode: Long, sha256: String = sha, prerelease: Boolean = false) {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse.Builder().code(200).addHeader("ETag", "\"e$versionCode\"").body(
            """{"tag_name":"v0.0.$versionCode","prerelease":$prerelease,"draft":false,"assets":[
              {"name":"app-release.apk","browser_download_url":"$base/app-release.apk"},
              {"name":"update.json","browser_download_url":"$base/update.json"}]}""").build())
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"versionCode":$versionCode,"versionName":"0.0.$versionCode","apkUrl":"$base/app-release.apk",
               "sha256":"$sha256","minSdk":26,"releaseNotes":"- Cambio","publishedAt":"2026-10-09T10:00:00Z"}""").build())
    }

    private fun comprobador(cache: CacheHttp = CacheMemoria()) =
        ComprobadorActualizaciones(http, "cazique/rclone-web", cache, server.url("/api").toString().trimEnd('/'), politica)

    @Test fun detectaVersionMayor() {
        release(105)
        val r = comprobador().comprobar(104, 34)
        assertTrue(r is ResultadoComprobacion.Disponible)
        assertEquals(105L, (r as ResultadoComprobacion.Disponible).info.versionCode)
        assertEquals("/api/repos/cazique/rclone-web/releases/latest", server.takeRequest().target)
    }

    @Test fun ignoraIgualOMenor() {
        release(104)
        assertTrue(comprobador().comprobar(104, 34) is ResultadoComprobacion.AlDia)
        release(100)
        assertTrue(comprobador().comprobar(104, 34) is ResultadoComprobacion.AlDia)
    }

    @Test fun respetaMinSdk() {
        release(200)
        assertTrue(comprobador().comprobar(104, 24) is ResultadoComprobacion.Incompatible)
    }

    @Test fun usaEtag() {
        val cache = CacheMemoria()
        release(105)
        comprobador(cache).comprobar(104, 34)
        server.enqueue(MockResponse.Builder().code(304).build())
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"versionCode":105,"versionName":"0.0.105","apkUrl":"${server.url("/app-release.apk")}","sha256":"$sha","minSdk":26}""").build())
        assertTrue(comprobador(cache).comprobar(104, 34) is ResultadoComprobacion.Disponible)
        server.takeRequest(); server.takeRequest()
        assertEquals("\"e105\"", server.takeRequest().headers["If-None-Match"])
    }

    @Test fun descargaYVerificaSha() {
        release(105)
        val info = (comprobador().comprobar(104, 34) as ResultadoComprobacion.Disponible).info
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(apk)).build())
        val f = DescargadorApk(http, politica, esperaMs = 1).descargar(info, tmp.newFile("a.apk"))
        assertEquals(sha, DescargadorApk.sha256(f))
    }

    @Test fun rechazaShaIncorrecto() {
        release(105, sha256 = "0".repeat(64))
        val info = (comprobador().comprobar(104, 34) as ResultadoComprobacion.Disponible).info
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(apk)).build())
        val destino = tmp.newFile("b.apk")
        val e = runCatching { DescargadorApk(http, politica, esperaMs = 1).descargar(info, destino) }.exceptionOrNull()
        assertTrue(e is ErrorActualizacion)
        assertFalse(destino.exists())
    }

    @Test fun reintentaTrasFallo() {
        release(105)
        val info = (comprobador().comprobar(104, 34) as ResultadoComprobacion.Disponible).info
        server.enqueue(MockResponse.Builder().code(500).build())
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(apk)).build())
        val f = DescargadorApk(http, politica, esperaMs = 1).descargar(info, tmp.newFile("c.apk"))
        assertEquals(sha, DescargadorApk.sha256(f))
    }

    @Test fun rechazaFirmaDistinta() {
        assertTrue(VerificacionFirma.misma(setOf("AA11"), setOf("aa11")))
        assertFalse(VerificacionFirma.misma(setOf("AA11"), setOf("BB22")))
        assertFalse(VerificacionFirma.misma(emptySet(), emptySet()))
    }

    @Test fun soloDominiosDeGithubPorHttps() {
        val p = PoliticaRed()
        assertTrue(p.permite("https://github.com/cazique/rclone-web/releases/download/v1/app-release.apk"))
        assertTrue(p.permite("https://release-assets.githubusercontent.com/x"))
        assertFalse(p.permite("http://github.com/x"))
        assertFalse(p.permite("https://evil.example.com/x"))
    }

    @Test fun preliminaresSoloSiSePiden() {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse.Builder().code(200).body(
            """[{"prerelease":true,"draft":false,"assets":[{"name":"update.json","browser_download_url":"$base/update.json"}]}]""").build())
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"versionCode":300,"versionName":"0.1.300","apkUrl":"$base/a.apk","sha256":"$sha"}""").build())
        val r = comprobador().comprobar(104, 34, incluirPreliminares = true)
        assertTrue(r is ResultadoComprobacion.Disponible && r.preliminar)
    }
}
