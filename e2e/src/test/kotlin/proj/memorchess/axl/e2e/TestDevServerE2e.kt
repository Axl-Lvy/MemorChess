package proj.memorchess.axl.e2e

import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotEndWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.auth.AccessToken
import proj.memorchess.axl.core.sync.DeviceId
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.SyncApiClient
import proj.memorchess.axl.core.sync.SyncRegisterOutcome
import proj.memorchess.axl.server.startDevServer

/** Proves the session `:server:devRun` writes is exactly what a dev client needs to sync. */
class TestDevServerE2e {

  private fun sessionFile(): Path =
    Files.createTempDirectory("dev-server").resolve("session.properties")

  private fun load(path: Path): Properties =
    Properties().apply { Files.newInputStream(path).use { load(it) } }

  @Test
  fun sessionFileIsComplete() {
    val path = sessionFile()

    startDevServer(preferredPort = 0, sessionFile = path).use { server ->
      val session = load(path)

      session.stringPropertyNames() shouldBe
        setOf("baseUrl", "accessToken", "expiresAt", "sub", "generation")
      session.getProperty("baseUrl") shouldBe server.baseUrl
      Instant.parse(session.getProperty("expiresAt")) shouldBeGreaterThanOrEqualTo
        Clock.System.now() + 29.days
    }
  }

  @Test
  fun tokenIsAccepted(): Unit = runBlocking {
    val path = sessionFile()

    startDevServer(preferredPort = 0, sessionFile = path).use {
      val session = load(path)
      val client = SyncApiClient(httpClient, "${session.getProperty("baseUrl")}/v1")

      client
        .registerDevice(
          AccessToken(session.getProperty("accessToken")),
          DeviceId.random(),
          DevicePlatform.JVM,
          afterReset = false,
        )
        .shouldBeInstanceOf<SyncRegisterOutcome.Ok>()
    }
  }

  @Test
  fun takenPortFallsBack() {
    ServerSocket(0).use { taken ->
      startDevServer(preferredPort = taken.localPort, sessionFile = sessionFile()).use { server ->
        server.baseUrl shouldNotEndWith ":${taken.localPort}"
      }
    }
  }

  @Test
  fun freePortIsUsed() {
    val port = ServerSocket(0).use { it.localPort }

    startDevServer(preferredPort = port, sessionFile = sessionFile()).use { server ->
      server.baseUrl shouldEndWith ":$port"
    }
  }

  private companion object {
    val httpClient =
      HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
  }
}
