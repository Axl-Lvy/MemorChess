package proj.memorchess.axl.server.routes

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

/** One year, for assets whose content hash is in their name. */
private const val HASHED_ASSET_MAX_AGE_SECONDS = 31_536_000

/** Content-hashed bundle file names, which a deploy renames instead of overwriting. */
private val HASHED_ASSET_PATTERN = Regex("""(composeApp|styles)\.[0-9a-f]{20}\.(js|css)""")

/** Path of the wasmJs sync redirect URI; must match the client's `SYNC_REDIRECT_PATH`. */
internal const val SYNC_OAUTH_CALLBACK_PATH = "/sync-oauth-callback"

/** Path of the Lichess OAuth redirect URI; must match the client's `LICHESS_REDIRECT_URI`. */
internal const val LICHESS_OAUTH_CALLBACK_PATH = "/oauth-callback"

/** Must match the client's `OAUTH_BROADCAST_CHANNEL_NAME`. */
private const val BROADCAST_CHANNEL_NAME = "memorchess-oauth"

/**
 * Callback page that broadcasts its URL on [BROADCAST_CHANNEL_NAME] and closes. Chrome detaches the
 * popup from its opener once it visits lichess.org, so only an origin scoped channel reaches it.
 */
private const val LICHESS_OAUTH_CALLBACK_BODY =
  "<!doctype html><title>MemorChess</title><script>" +
    "var c=new BroadcastChannel('$BROADCAST_CHANNEL_NAME');" +
    "c.postMessage({href:location.href});" +
    "c.close();" +
    "window.close();" +
    "</script>"

/** Serves the compiled wasmJs frontend bundle from [staticDir]; `null` disables it. */
internal fun Application.staticFrontendModule(staticDir: File?) {
  if (staticDir == null) return
  routing { staticFrontendRoutes(staticDir) }
}

/**
 * Mounts the frontend bundle at `/` along with both OAuth callback pages; any other unmatched path
 * is a 404.
 */
internal fun Route.staticFrontendRoutes(staticDir: File) {
  // Serves the shell so the wasmJs redirect sign in can cold boot here.
  get(SYNC_OAUTH_CALLBACK_PATH) {
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondFile(File(staticDir, "index.html"))
  }
  // A real body, not a 404: Firefox swaps an empty 4xx body for its own page, never closing it.
  get(LICHESS_OAUTH_CALLBACK_PATH) {
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondText(LICHESS_OAUTH_CALLBACK_BODY, ContentType.Text.Html)
  }
  staticFiles("/", staticDir) {
    cacheControl { file ->
      if (file.extension == "wasm" || HASHED_ASSET_PATTERN.matches(file.name)) {
        listOf(CacheControl.MaxAge(maxAgeSeconds = HASHED_ASSET_MAX_AGE_SECONDS))
      } else {
        // index.html and unhashed files could point at a previous deploy's bundle.
        listOf(CacheControl.NoCache(visibility = null))
      }
    }
  }
}
