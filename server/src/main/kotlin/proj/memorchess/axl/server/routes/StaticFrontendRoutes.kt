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

/** How long a content-hashed static asset may be cached, in seconds: one year. */
private const val HASHED_ASSET_MAX_AGE_SECONDS = 31_536_000

/**
 * Path portion of the wasmJs sync/OIDC redirect URI; kept in sync with the client's own copy of
 * this string (`SYNC_REDIRECT_PATH` in `:composeApp`) by the design spec, not by shared code.
 */
internal const val SYNC_OAUTH_CALLBACK_PATH = "/sync-oauth-callback"

/**
 * Path portion of the Lichess OAuth redirect URI; kept in sync with the client's own copy of this
 * string (`LICHESS_REDIRECT_URI` in `LichessRedirectUri.wasmJs.kt`) by hand, not by shared code.
 */
internal const val LICHESS_OAUTH_CALLBACK_PATH = "/oauth-callback"

/**
 * Name of the `BroadcastChannel` the Lichess OAuth callback page and the wasmJs popup's opener
 * share; kept in sync with the client's own copy of this string (`OAUTH_BROADCAST_CHANNEL_NAME` in
 * `OAuthLauncher.wasmJs.kt`) by hand, not by shared code.
 */
private const val BROADCAST_CHANNEL_NAME = "memorchess-oauth"

/**
 * Placeholder body served at [LICHESS_OAUTH_CALLBACK_PATH]. It broadcasts its own URL on a
 * [BROADCAST_CHANNEL_NAME] `BroadcastChannel` and closes itself; the wasmJs popup's opener listens
 * on that same channel and reads the OAuth code from it. A `BroadcastChannel` is used instead of
 * `window.opener.postMessage`, because on Chrome the popup gets detached from its opener once it
 * navigates to `lichess.org`: neither `popup.closed` nor `window.opener` can be trusted afterwards,
 * even once the popup navigates back to our own origin, so nothing keyed on the window relationship
 * (polling `popup.closed`/`popup.location.href`, or posting through `window.opener`) can reach the
 * opener. `BroadcastChannel` is scoped by origin, not by window reference, so it is unaffected. A
 * user is not expected to ever see this page rendered.
 */
private const val LICHESS_OAUTH_CALLBACK_BODY =
  "<!doctype html><title>MemorChess</title><script>" +
    "var c=new BroadcastChannel('$BROADCAST_CHANNEL_NAME');" +
    "c.postMessage({href:location.href});" +
    "c.close();" +
    "window.close();" +
    "</script>"

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
 * is one deliberate exception, serving the shell so the wasmJs redirect sign-in flow can cold-boot
 * there. [LICHESS_OAUTH_CALLBACK_PATH] is a second one: it must answer with a real body rather than
 * falling through to a bare 404, because the body itself is what closes the popup and broadcasts
 * the OAuth code (see [LICHESS_OAUTH_CALLBACK_BODY]); a 404 would leave the popup open
 * indefinitely. Firefox also replaces any 4xx or 5xx response that has an empty body with its own
 * internal error page, which would never run that script at all. Every other unmatched path still
 * falls through to a plain 404, since no `default()` fallback is configured.
 */
internal fun Route.staticFrontendRoutes(staticDir: File) {
  get(SYNC_OAUTH_CALLBACK_PATH) {
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondFile(File(staticDir, "index.html"))
  }
  get(LICHESS_OAUTH_CALLBACK_PATH) {
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondText(LICHESS_OAUTH_CALLBACK_BODY, ContentType.Text.Html)
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
