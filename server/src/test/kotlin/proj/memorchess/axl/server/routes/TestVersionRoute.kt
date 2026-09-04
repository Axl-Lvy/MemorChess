package proj.memorchess.axl.server.routes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import proj.memorchess.axl.core.sync.SYNC_JSON

class TestVersionRoute {

  @Test
  fun `GET v1 version answers 200 with a non-blank sha`() = testApplication {
    install(ContentNegotiation) { json(SYNC_JSON) }
    application { routing { versionRoute() } }

    val response = client.get("/v1/version")

    response.status shouldBe HttpStatusCode.OK
    val body = SYNC_JSON.decodeFromString<VersionResponse>(response.bodyAsText())
    body.sha.shouldNotBeBlank()
  }
}
