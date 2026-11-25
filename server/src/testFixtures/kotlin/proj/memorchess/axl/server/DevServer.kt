package proj.memorchess.axl.server

import java.net.BindException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** The fake user every dev client signs in as. */
private const val DEV_USER = "dev-user"

private val DEV_TOKEN_LIFETIME = 30.days

/**
 * Starts a local dev server for `./gradlew :server:devRun` and writes the session a dev client
 * reads to sign in to it. Falls back to a free port when [preferredPort] is taken, so two
 * worktrees' servers can run side by side; the session always holds the real URL.
 */
fun startDevServer(preferredPort: Int, sessionFile: Path): E2eServer {
  val server =
    try {
      startE2eServer(preferredPort)
    } catch (e: Exception) {
      if (!e.isBindFailure()) throw e
      startE2eServer(0)
    }
  // Stated slightly before the token's own expiry, so the client stops using it first.
  val expiresAt = Clock.System.now() + DEV_TOKEN_LIFETIME
  val session =
    Properties().apply {
      setProperty("baseUrl", server.baseUrl)
      setProperty("accessToken", server.tokenFor(DEV_USER, DEV_TOKEN_LIFETIME))
      setProperty("expiresAt", expiresAt.toString())
      setProperty("sub", DEV_USER)
      setProperty("generation", UUID.randomUUID().toString())
    }
  writeAtomically(sessionFile, session)
  return server
}

/** Runs the dev server until the process is stopped; configured only from the environment. */
fun main() {
  val port = System.getenv("MEMORCHESS_DEV_PORT")?.toInt() ?: 0
  val sessionFile =
    Path.of(System.getenv("MEMORCHESS_DEV_SESSION_FILE") ?: error("MEMORCHESS_DEV_SESSION_FILE"))
  val server = startDevServer(port, sessionFile)
  val stopped = CountDownLatch(1)
  Runtime.getRuntime()
    .addShutdownHook(
      Thread {
        // A stale session would point dev clients at a server that is gone.
        Files.deleteIfExists(sessionFile)
        server.close()
        stopped.countDown()
      },
    )
  println("Dev sync server running at ${server.baseUrl} (session: $sessionFile)")
  stopped.await()
}

private fun writeAtomically(target: Path, properties: Properties) {
  Files.createDirectories(target.toAbsolutePath().parent)
  val temp = Files.createTempFile(target.toAbsolutePath().parent, "session", ".tmp")
  Files.newOutputStream(temp).use { properties.store(it, "Written by :server:devRun") }
  Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

private fun Throwable.isBindFailure(): Boolean =
  generateSequence(this) { it.cause }.any { it is BindException }
