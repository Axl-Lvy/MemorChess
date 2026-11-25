package proj.memorchess.axl.dev

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.auth.AccessToken
import proj.memorchess.axl.core.auth.Account

class TestDevSession {

  private val complete =
    mapOf(
      "baseUrl" to "http://127.0.0.1:21234",
      "accessToken" to "jwt",
      "expiresAt" to "2030-01-01T00:00:00Z",
      "sub" to "dev-user",
      "generation" to "gen-1",
    )

  private fun sessionFile(entries: Map<String, String>): File {
    val file = Files.createTempFile("session", ".properties").toFile()
    file.writeText(entries.entries.joinToString("\n") { (key, value) -> "$key=$value" })
    return file
  }

  @Test
  fun readsAllKeys() {
    val session = readDevSession(sessionFile(complete))

    session shouldBe
      DevSession(
        baseUrl = "http://127.0.0.1:21234",
        accessToken = AccessToken("jwt"),
        expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        account = Account("dev-user", "Dev user"),
        generation = DevServerGeneration("gen-1"),
      )
  }

  @Test
  fun absentFileIsNoSession() {
    readDevSession(File("/nope/session.properties")) shouldBe null
  }

  @Test
  fun noFileConfiguredIsNoSession() {
    readDevSession(null) shouldBe null
  }

  @Test
  fun malformedFileNamesKey() {
    val file = sessionFile(complete - "accessToken")

    val error = shouldThrow<IllegalStateException> { readDevSession(file) }

    error.message shouldContain "accessToken"
  }
}
