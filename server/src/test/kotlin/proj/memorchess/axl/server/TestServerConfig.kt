package proj.memorchess.axl.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.io.path.createTempDirectory
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
      "SYNC_R2_ENDPOINT" to "https://accountid.r2.cloudflarestorage.com",
      "SYNC_R2_BUCKET" to "memorchess-repertoires",
      "SYNC_R2_ACCESS_KEY_ID" to "test-key",
      "SYNC_R2_SECRET_ACCESS_KEY" to "test-secret",
      "SYNC_ADMIN_TOKEN" to "test-admin-token",
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
  fun `reads the r2 and admin configuration`() {
    val result = config()

    result.r2Endpoint.toString() shouldBe "https://accountid.r2.cloudflarestorage.com"
    result.r2Bucket shouldBe "memorchess-repertoires"
    result.r2AccessKeyId shouldBe "test-key"
    result.r2SecretAccessKey shouldBe "test-secret"
    result.adminToken shouldBe "test-admin-token"
  }

  @Test
  fun `refuses a non absolute r2 endpoint`() {
    val failure =
      shouldThrow<IllegalStateException> { config(mapOf("SYNC_R2_ENDPOINT" to "not-a-url")) }

    failure.message!! shouldContain "SYNC_R2_ENDPOINT"
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

  @Test
  fun `static dir is null when SYNC_STATIC_DIR is absent`() {
    config().staticDir shouldBe null
  }

  @Test
  fun `static dir is read when it points at an existing directory`() {
    val tempDir = createTempDirectory(prefix = "server-config-test").toFile()

    val result = config(mapOf("SYNC_STATIC_DIR" to tempDir.absolutePath))

    result.staticDir shouldBe tempDir
  }

  @Test
  fun `refuses a static dir that does not exist`() {
    val failure =
      shouldThrow<IllegalStateException> {
        config(mapOf("SYNC_STATIC_DIR" to "/does/not/exist"))
      }

    failure.message!! shouldContain "SYNC_STATIC_DIR"
  }
}
