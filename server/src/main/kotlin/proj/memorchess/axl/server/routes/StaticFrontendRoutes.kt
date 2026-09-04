package proj.memorchess.axl.server.routes

import io.ktor.http.CacheControl
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import java.io.File

/** How long a content-hashed static asset may be cached, in seconds: one year. */
private const val HASHED_ASSET_MAX_AGE_SECONDS = 31_536_000

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
 * other file (unhashed HTML/JS/CSS/resources) is revalidated on every request, since a stale
 * cached `composeApp.js` could reference a `.wasm` hash from a previous deploy. An unmatched path
 * (e.g. `/oauth-callback`, read only for its URL by the wasmJs OAuth popup flow and never rendered)
 * falls through to a plain 404 rather than `index.html` — no `default()` fallback is configured,
 * deliberately.
 */
internal fun Route.staticFrontendRoutes(staticDir: File) {
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
