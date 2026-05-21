package proj.memorchess.axl.core.auth

import co.touchlab.kermit.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Desktop OAuth launcher.
 *
 * Spins up a one shot HTTP server on `127.0.0.1:<random port>`, opens the system browser at the
 * Lichess authorize URL, and resumes with the authorization code as soon as Lichess redirects back
 * to `http://127.0.0.1:<port>/callback?code=...`.
 *
 * The caller must pass the loopback URL as the `redirect_uri` so Lichess sends the browser back
 * here. `[OAuthLauncher.launch]` ignores [redirectUri] for binding decisions and parses the port
 * out of it.
 */
actual class OAuthLauncher {

  actual suspend fun launch(
    authorizationUrl: String,
    redirectUri: String,
    expectedState: String,
  ): OAuthLaunchResult {
    val callbackUri = URI(redirectUri)
    val port = callbackUri.port.takeIf { it > 0 } ?: DEFAULT_PORT
    val path = callbackUri.path.ifEmpty { "/callback" }
    val server =
      try {
        HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
      } catch (e: Exception) {
        LOGGER.w(e) { "Could not start loopback server on port $port" }
        return OAuthLaunchResult.Error(OAuthLaunchError.PLATFORM_ERROR)
      }
    return try {
      suspendCancellableCoroutine { cont ->
        server.createContext(path) { exchange ->
          val result = handleCallback(exchange, expectedState)
          respond(exchange, result)
          exchange.close()
          if (cont.isActive) cont.resume(result)
        }
        server.executor = null
        server.start()
        cont.invokeOnCancellation { server.stop(0) }
        try {
          openBrowser(authorizationUrl)
        } catch (e: Exception) {
          LOGGER.w(e) { "Could not open the system browser" }
          if (cont.isActive)
            cont.resume(OAuthLaunchResult.Error(OAuthLaunchError.BROWSER_UNAVAILABLE))
        }
      }
    } finally {
      withContext(Dispatchers.IO) { server.stop(0) }
    }
  }

  private fun openBrowser(url: String) {
    if (tryDesktopBrowse(url)) return
    if (tryShellBrowse(url)) return
    throw UnsupportedOperationException("No working browser launcher on this platform")
  }

  private fun tryDesktopBrowse(url: String): Boolean {
    return try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
        true
      } else {
        false
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Desktop.browse failed; trying shell fallback" }
      false
    }
  }

  /**
   * Falls back to the OS shell when AWT's [Desktop] integration is unavailable. Linux JDKs
   * routinely return `false` from `Desktop.Action.BROWSE.isSupported()` outside of GNOME, so this
   * path is the common one on Linux.
   */
  private fun tryShellBrowse(url: String): Boolean {
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    val commands: List<Array<String>> =
      when {
        os.contains("mac") -> listOf(arrayOf("open", url))
        os.contains("windows") -> listOf(arrayOf("cmd", "/c", "start", "", url))
        else -> listOf(arrayOf("xdg-open", url), arrayOf("gio", "open", url))
      }
    for (cmd in commands) {
      try {
        val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        // Do not wait for the browser to exit; just confirm the launcher started.
        if (process.isAlive || process.exitValue() == 0) return true
      } catch (e: Exception) {
        LOGGER.i { "Browser command ${cmd.joinToString(" ")} failed: ${e.message}" }
      }
    }
    return false
  }

  private fun handleCallback(exchange: HttpExchange, expectedState: String): OAuthLaunchResult {
    val query = exchange.requestURI.rawQuery ?: ""
    val params = parseQuery(query)
    val state = params["state"]
    val code = params["code"]
    return when {
      code == null -> OAuthLaunchResult.Error(OAuthLaunchError.MISSING_CODE)
      state != expectedState -> OAuthLaunchResult.Error(OAuthLaunchError.STATE_MISMATCH)
      else -> OAuthLaunchResult.Ok(code)
    }
  }

  private fun respond(exchange: HttpExchange, result: OAuthLaunchResult) {
    val (status, body) =
      when (result) {
        is OAuthLaunchResult.Ok -> 200 to BODY_OK
        is OAuthLaunchResult.Error,
        OAuthLaunchResult.Cancelled -> 400 to BODY_ERROR
      }
    val bytes = body.encodeToByteArray()
    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  private fun parseQuery(query: String): Map<String, String> =
    query
      .split('&')
      .filter { it.isNotEmpty() }
      .mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
        val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
        key to value
      }
      .toMap()

  private companion object {
    const val DEFAULT_PORT = 9009
    const val BODY_OK =
      "<!doctype html><html><body><h2>MemorChess: signed in</h2>" +
        "<p>You can close this tab and return to MemorChess.</p></body></html>"
    const val BODY_ERROR =
      "<!doctype html><html><body><h2>MemorChess: sign in failed</h2>" +
        "<p>Return to the app and try again.</p></body></html>"
  }
}

private val LOGGER = Logger.withTag("OAuthLauncher.jvm")
