package proj.memorchess.axl.test_util

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

object DockerServerManager {

  @Volatile private var started = false

  private val projectRoot: File by lazy {
    // Walk up from CWD to find docker-compose.yml
    var dir = File(System.getProperty("user.dir"))
    while (dir.parentFile != null) {
      if (File(dir, "docker-compose.yml").exists()) return@lazy dir
      dir = dir.parentFile
    }
    File(System.getProperty("user.dir"))
  }

  @JvmStatic
  fun ensureRunning() {
    if (started) return
    synchronized(this) {
      if (started) return
      if (isServerReady()) {
        println("Server already running on localhost:8080")
        started = true
        return
      }
      startDockerCompose()
      waitForServer()
      started = true
    }
  }

  private fun startDockerCompose() {
    println("Starting Docker Compose from ${projectRoot.absolutePath}...")
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val command =
      if (isWindows) {
        listOf("cmd", "/c", "docker", "compose", "up", "-d", "--build", "--wait")
      } else {
        listOf("docker", "compose", "up", "-d", "--build", "--wait")
      }
    val process =
      ProcessBuilder(command)
        .directory(projectRoot)
        .redirectErrorStream(true)
        .inheritIO()
        .start()
    val finished = process.waitFor(5, TimeUnit.MINUTES)
    if (!finished) {
      process.destroyForcibly()
      throw RuntimeException("docker compose up timed out after 5 minutes")
    }
    val exitCode = process.exitValue()
    if (exitCode != 0) {
      throw RuntimeException("docker compose up failed (exit $exitCode)")
    }
    println("Docker Compose started successfully.")
  }

  private fun waitForServer(timeoutMs: Long = 120_000) {
    println("Waiting for server to be ready...")
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (isServerReady()) {
        println("Server is ready.")
        return
      }
      Thread.sleep(1000)
    }
    throw RuntimeException("Server did not become ready within ${timeoutMs / 1000}s")
  }

  private fun isServerReady(): Boolean {
    return try {
      val conn = URI("http://localhost:8080/swagger").toURL().openConnection() as HttpURLConnection
      conn.connectTimeout = 2000
      conn.readTimeout = 2000
      conn.requestMethod = "GET"
      val code = conn.responseCode
      conn.disconnect()
      code in 200..399
    } catch (_: Exception) {
      false
    }
  }
}
