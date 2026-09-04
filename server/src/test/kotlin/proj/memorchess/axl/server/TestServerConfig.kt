package proj.memorchess.axl.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class TestServerConfig {

  private val complete =
    mapOf(
      "SYNC_DB_URL" to "jdbc:postgresql://db:5432/memorchess",
      "SYNC_DB_USER" to "chess",
      "SYNC_DB_PASSWORD" to "secret",
      "SYNC_JWT_ISSUER" to "https://issuer.example/",
      "SYNC_JWT_AUDIENCE" to "memorchess",
      "SYNC_JWKS_URL" to "https://issuer.example/.well-known/jwks.json",
    )

  private fun config(overrides: Map<String, String?> = emptyMap()) =
    serverConfigFromEnv((complete + overrides)::get)

  @Test
  fun `reads every value from the environment`() {
    val result = config()

    result.jdbcUrl shouldBe "jdbc:postgresql://db:5432/memorchess"
    result.dbUser shouldBe "chess"
    result.dbPassword shouldBe "secret"
    result.jwtIssuer shouldBe "https://issuer.example/"
    result.jwtAudience shouldBe "memorchess"
    result.jwksUrl.toString() shouldBe "https://issuer.example/.well-known/jwks.json"
  }

  @Test
  fun `defaults the port when it is absent`() {
    config(mapOf("SYNC_PORT" to null)).port shouldBe 8080
  }

  @Test
  fun `reads the port when it is present`() {
    config(mapOf("SYNC_PORT" to "9443")).port shouldBe 9443
  }

  @Test
  fun `refuses a port that is not a number`() {
    val failure = shouldThrow<IllegalStateException> { config(mapOf("SYNC_PORT" to "http")) }

    failure.message!! shouldContain "SYNC_PORT"
  }

  @Test
  fun `refuses a port outside the legal range`() {
    shouldThrow<IllegalStateException> { config(mapOf("SYNC_PORT" to "0")) }
    shouldThrow<IllegalStateException> { config(mapOf("SYNC_PORT" to "65536")) }
  }

  @Test
  fun `names the variable that is missing`() {
    for (variable in complete.keys) {
      val failure = shouldThrow<IllegalStateException> { config(mapOf(variable to null)) }

      failure.message!! shouldContain variable
    }
  }

  @Test
  fun `treats a blank variable as missing`() {
    val failure = shouldThrow<IllegalStateException> { config(mapOf("SYNC_DB_USER" to "   ")) }

    failure.message!! shouldContain "SYNC_DB_USER"
  }

  @Test
  fun `refuses a JWKS URL that is not absolute`() {
    val failure =
      shouldThrow<IllegalStateException> { config(mapOf("SYNC_JWKS_URL" to "/jwks.json")) }

    failure.message!! shouldContain "SYNC_JWKS_URL"
  }

  @Test
  fun `refuses a JWKS URL that is not http`() {
    val failure =
      shouldThrow<IllegalStateException> { config(mapOf("SYNC_JWKS_URL" to "file:///etc/jwks")) }

    failure.message!! shouldContain "SYNC_JWKS_URL"
  }
}
