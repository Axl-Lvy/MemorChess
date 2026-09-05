package proj.memorchess.axl.server.routes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class TestStaticFrontendRoutes {

  private fun frontendDir(): File {
    val dir = createTempDirectory(prefix = "static-frontend-test").toFile()
    File(dir, "index.html").writeText("<html>app</html>")
    File(dir, "composeApp.js").writeText("console.log('app')")
    File(dir, "182a9d40939c859861e7.wasm").writeBytes(byteArrayOf(0, 1, 2))
    return dir
  }

  @Test
  fun `a hashed wasm file is cached for a year`() = testApplication {
    application { routing { staticFrontendRoutes(frontendDir()) } }

    val response = client.get("/182a9d40939c859861e7.wasm")

    response.status shouldBe HttpStatusCode.OK
    response.headers[HttpHeaders.CacheControl]!! shouldContain "max-age=31536000"
  }

  @Test
  fun `index html is never cached`() = testApplication {
    application { routing { staticFrontendRoutes(frontendDir()) } }

    val response = client.get("/index.html")

    response.status shouldBe HttpStatusCode.OK
    response.headers[HttpHeaders.CacheControl]!! shouldContain "no-cache"
  }

  @Test
  fun `an unhashed js file is never cached`() = testApplication {
    application { routing { staticFrontendRoutes(frontendDir()) } }

    val response = client.get("/composeApp.js")

    response.headers[HttpHeaders.CacheControl]!! shouldContain "no-cache"
  }

  @Test
  fun `an unmatched path like the oauth callback 404s instead of falling back to index html`() =
    testApplication {
      application { routing { staticFrontendRoutes(frontendDir()) } }

      val response = client.get("/oauth-callback?code=abc&state=xyz")

      response.status shouldBe HttpStatusCode.NotFound
    }

  @Test
  fun `an existing api route still wins over the static catch all`() = testApplication {
    application {
      routing {
        get("/v1/health") { call.respondText("ok") }
        staticFrontendRoutes(frontendDir())
      }
    }

    val response = client.get("/v1/health")

    response.status shouldBe HttpStatusCode.OK
    response.bodyAsText() shouldBe "ok"
  }

  @Test
  fun `staticFrontendModule installs no routes when staticDir is null`() = testApplication {
    application { staticFrontendModule(null) }

    client.get("/").status shouldBe HttpStatusCode.NotFound
  }

  @Test
  fun `staticFrontendModule serves the frontend when staticDir is configured`() = testApplication {
    application { staticFrontendModule(frontendDir()) }

    client.get("/index.html").status shouldBe HttpStatusCode.OK
  }
}
