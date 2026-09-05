package proj.memorchess.axl.server.routes

import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

/** How long a content-hashed static asset may be cached, in seconds: one year. */
private const val HASHED_ASSET_MAX_AGE_SECONDS = 31_536_000

/**
 * Path portion of the wasmJs sync/OIDC redirect URI; kept in sync with the client's own copy of
 * this string (`SYNC_REDIRECT_PATH` in `:composeApp`) by the design spec, not by shared code.
 */
internal const val SYNC_OAUTH_CALLBACK_PATH = "/sync-oauth-callback"

/**
 * Serves the compiled wasmJs frontend bundle from [staticDir], if configured.
 *
 * A `null` [staticDir] disables frontend serving entirely (local `:server:run`, tests), since only
 * the Docker image ships a bundle to serve.
 */
internal fun Application.staticFrontendModule(staticDir: File?) {
  if (staticDir == null) return
  routing { staticFrontendRoutes(staticDir) }
}

/**
 * Mounts the frontend bundle at `/`. Content-hashed `*.wasm` files are cached for a year; every
 * other file (unhashed HTML/JS/CSS/resources) is revalidated on every request, since a stale cached
 * `composeApp.js` could reference a `.wasm` hash from a previous deploy. [SYNC_OAUTH_CALLBACK_PATH]
 * is the one deliberate exception, serving the shell so the wasmJs redirect sign-in flow can
 * cold-boot there. Every other unmatched path (e.g. `/oauth-callback`, Lichess's own redirect URI,
 * read only for its URL by that popup flow and never rendered) falls through to a plain 404 rather
 * than `index.html` — no `default()` fallback is configured, deliberately.
 */
internal fun Route.staticFrontendRoutes(staticDir: File) {
  get(SYNC_OAUTH_CALLBACK_PATH) {
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondFile(File(staticDir, "index.html"))
  }
  staticFiles("/", staticDir) {
    cacheControl { file ->
      if (file.extension == "wasm") {
        listOf(CacheControl.MaxAge(maxAgeSeconds = HASHED_ASSET_MAX_AGE_SECONDS))
      } else {
        listOf(CacheControl.NoCache(visibility = null))
      }
    }
  }
}
