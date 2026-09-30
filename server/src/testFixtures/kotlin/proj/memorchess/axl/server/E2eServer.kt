package proj.memorchess.axl.server

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import proj.memorchess.axl.server.auth.TEST_AUDIENCE
import proj.memorchess.axl.server.auth.TEST_ISSUER
import proj.memorchess.axl.server.auth.TestJwkProvider
import proj.memorchess.axl.server.auth.TestSigningKey
import proj.memorchess.axl.server.db.PostgresTestDb
import proj.memorchess.axl.server.repertoire.InMemoryRepertoireBlobStore
import proj.memorchess.axl.server.repertoire.RepertoireStore
import proj.memorchess.axl.server.sync.SyncStore

/**
 * Every request of an end to end run comes from 127.0.0.1, so the IP keyed tiers must never trip.
 */
private val UNLIMITED = RateLimitTier(limit = 1_000_000, refillPeriod = 1.minutes)

private val E2E_CONFIG =
  ServerConfig(
    port = 0,
    jdbcUrl = "unused",
    dbUser = "unused",
    dbPassword = "unused",
    jwtIssuer = TEST_ISSUER,
    jwtAudience = TEST_AUDIENCE,
    jwksUrl = URI("https://issuer.test/jwks.json"),
    r2Endpoint = URI("https://r2.test/"),
    r2Bucket = "unused",
    r2AccessKeyId = "unused",
    r2SecretAccessKey = "unused",
  )

/** A real `:server` on a random local port, backed by the shared test Postgres. */
class E2eServer
internal constructor(
  private val server: EmbeddedServer<*, *>,
  private val key: TestSigningKey,
  port: Int,
) : AutoCloseable {

  /** Root URL without a trailing slash, for example `http://127.0.0.1:41234`. */
  val baseUrl: String = "http://127.0.0.1:$port"

  /** A user id no other caller of this server has used. */
  fun newUserId(): String = "e2e-${UUID.randomUUID()}"

  /** A signed access token the server accepts for [userId]. */
  fun tokenFor(userId: String): String = key.token(subject = userId)

  /** Stops the server. */
  override fun close() {
    server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
  }
}

/** Starts an [E2eServer] serving the production routes. Blocks until the port is bound. */
fun startE2eServer(): E2eServer {
  val key = TestSigningKey("e2e")
  val dataSource = PostgresTestDb.dataSource()
  val server =
    embeddedServer(Netty, port = 0, host = "127.0.0.1") {
        serverModules(
          config = E2E_CONFIG,
          jwkProvider = TestJwkProvider(key),
          syncStore = SyncStore(dataSource),
          repertoireStore = RepertoireStore(dataSource, InMemoryRepertoireBlobStore()),
          readiness = { true },
          rateLimits = RateLimitTiers(UNLIMITED, UNLIMITED, UNLIMITED, UNLIMITED),
        )
      }
      .start(wait = false)
  val port = runBlocking { server.engine.resolvedConnectors().first().port }
  return E2eServer(server, key, port)
}
