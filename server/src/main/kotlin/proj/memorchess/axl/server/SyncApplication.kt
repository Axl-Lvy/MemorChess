package proj.memorchess.axl.server

import com.auth0.jwk.JwkProvider
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.server.auth.Caller
import proj.memorchess.axl.server.auth.installJwtAuth
import proj.memorchess.axl.server.routes.syncRoutes
import proj.memorchess.axl.server.routes.versionRoute
import proj.memorchess.axl.server.sync.QuotaExceededException
import proj.memorchess.axl.server.sync.SyncStore

/**
 * Largest request body accepted, checked against the declared length so nothing oversized is ever
 * buffered. A full sync batch is orders of magnitude smaller than this.
 */
internal const val MAX_BODY_BYTES: Long = 8L * 1024 * 1024

private val logger = LoggerFactory.getLogger("proj.memorchess.axl.server")

/** A request that exceeded a server side cap, whether in bytes or in row count. */
internal class TooLargeException(message: String) : Exception(message)

/** One named budget: requests allowed per [refillPeriod], reset in full at each period's end. */
internal data class RateLimitTier(val limit: Int, val refillPeriod: Duration)

/**
 * Every rate limit tier the server enforces, overridable so a test can use a tiny budget instead of
 * firing production sized traffic at itself.
 *
 * @property syncWrite Authenticated writes, keyed by caller: `/v1/sync` push, `/v1/me` deletion,
 *   publishing and removing a repertoire.
 * @property syncRead Authenticated reads, keyed by caller: `/v1/sync` pull.
 * @property publicRead Anonymous catalog and blob reads, keyed by caller IP.
 * @property admin The moderation route, keyed by caller IP. It carries no credential of its own
 *   (see [proj.memorchess.axl.server.routes.repertoireModule]'s KDoc): this budget only slows down
 *   whoever reaches it, and does not stand in for the missing credential. Both this tier and
 *   [publicRead] trust [clientIp], which in turn trusts that only Cloudflare can reach the origin;
 *   see that function's KDoc for what breaks if that stops being true.
 */
internal data class RateLimitTiers(
  val syncWrite: RateLimitTier = RateLimitTier(60, 1.minutes),
  val syncRead: RateLimitTier = RateLimitTier(300, 1.minutes),
  val publicRead: RateLimitTier = RateLimitTier(300, 1.minutes),
  val admin: RateLimitTier = RateLimitTier(30, 1.minutes),
)

internal val RATE_LIMIT_SYNC_WRITE = RateLimitName("sync-write")
internal val RATE_LIMIT_SYNC_READ = RateLimitName("sync-read")
internal val RATE_LIMIT_PUBLIC_READ = RateLimitName("public-read")
internal val RATE_LIMIT_ADMIN = RateLimitName("admin")

/**
 * Assembles the server: plugins, error mapping, probes and routes.
 *
 * Every collaborator is a parameter so a test builds the same module production does.
 *
 * @param readiness Whether the server can currently do work, answered by `/ready`.
 * @param clock Source of server time, substituted in tests that pin the skew boundary.
 * @param rateLimits The request budgets to enforce, substituted in tests that need to exhaust one.
 */
internal fun Application.syncModule(
  config: ServerConfig,
  jwkProvider: JwkProvider,
  store: SyncStore,
  readiness: suspend () -> Boolean,
  clock: () -> Instant = Clock.System::now,
  rateLimits: RateLimitTiers = RateLimitTiers(),
) {
  install(ContentNegotiation) { json(SYNC_JSON) }
  installErrorMapping()
  installBodySizeGuard()
  installRateLimiting(rateLimits)
  installJwtAuth(config, jwkProvider)

  routing {
    versionRoute()
    get("/health") { call.respondText("ok") }
    get("/ready") {
      if (readiness()) call.respondText("ok")
      else
        call.respond(
          HttpStatusCode.ServiceUnavailable,
          ApiError(ApiErrorCode.INTERNAL, "the server is not ready"),
        )
    }
    syncRoutes(store, clock)
  }
}

/**
 * Installs the named rate limit tiers, if not already installed.
 *
 * [syncModule] and [proj.memorchess.axl.server.routes.repertoireModule] each call this, and either
 * one may run first: production always installs both on the same `Application`, but a test may
 * mount `repertoireModule` on its own (see its own tests), so neither module can assume the other
 * already registered these tiers.
 */
internal fun Application.installRateLimiting(tiers: RateLimitTiers = RateLimitTiers()) {
  if (pluginOrNull(RateLimit) != null) return
  install(RateLimit) {
    register(RATE_LIMIT_SYNC_WRITE) {
      rateLimiter(limit = tiers.syncWrite.limit, refillPeriod = tiers.syncWrite.refillPeriod)
      requestKey { call -> call.callerKey() }
    }
    register(RATE_LIMIT_SYNC_READ) {
      rateLimiter(limit = tiers.syncRead.limit, refillPeriod = tiers.syncRead.refillPeriod)
      requestKey { call -> call.callerKey() }
    }
    register(RATE_LIMIT_PUBLIC_READ) {
      rateLimiter(limit = tiers.publicRead.limit, refillPeriod = tiers.publicRead.refillPeriod)
      requestKey { call -> call.clientIp() }
    }
    register(RATE_LIMIT_ADMIN) {
      rateLimiter(limit = tiers.admin.limit, refillPeriod = tiers.admin.refillPeriod)
      requestKey { call -> call.clientIp() }
    }
  }
}

/**
 * The authenticated caller's user id, or its IP when called from a route with no principal.
 *
 * The fallback never actually triggers on a route wrapped in `authenticate`: an unauthenticated
 * call never reaches a nested rate limiter, since the authentication challenge answers first. It
 * exists so this never throws if a route is ever rewired without that wrapping.
 */
private fun ApplicationCall.callerKey(): String = principal<Caller>()?.userId ?: clientIp()

/**
 * The caller's IP, trusting Cloudflare's own header over the socket address.
 *
 * The deployment sits entirely behind Cloudflare, so [io.ktor.server.request.ApplicationRequest]'s
 * remote host is Cloudflare's edge, the same handful of addresses for every caller. Keying an
 * anonymous rate limit on that would limit all callers together rather than each on their own.
 *
 * This header is trustworthy only because the origin is reachable exclusively through Cloudflare:
 * nothing here stops a direct caller from setting it to a fresh value on every request. If the
 * origin is ever exposed directly, [RateLimitTiers.publicRead] and [RateLimitTiers.admin] are both
 * trivially bypassed; [RateLimitTiers.syncWrite] and [RateLimitTiers.syncRead] are unaffected,
 * since those key on the verified JWT subject instead.
 */
private fun ApplicationCall.clientIp(): String =
  request.header("CF-Connecting-IP") ?: request.local.remoteHost

/**
 * Maps every failure to an [ApiError], so no stack trace, SQL text or exception class name reaches
 * a caller.
 */
private fun Application.installErrorMapping() {
  install(StatusPages) {
    // The RateLimit plugin answers a rejection itself, with an empty body; this rewrites it to the
    // same ApiError shape as every other failure, rather than every client learning a second body
    // format just for this one status.
    status(HttpStatusCode.TooManyRequests) { call, status ->
      call.respond(status, ApiError(ApiErrorCode.RATE_LIMITED, "too many requests, slow down"))
    }
    exception<BadRequestException> { call, cause ->
      call.respond(
        HttpStatusCode.BadRequest,
        ApiError(ApiErrorCode.BAD_REQUEST, cause.message ?: "malformed request"),
      )
    }
    exception<SerializationException> { call, _ ->
      call.respond(
        HttpStatusCode.BadRequest,
        ApiError(ApiErrorCode.BAD_REQUEST, "the request body could not be decoded"),
      )
    }
    exception<TooLargeException> { call, cause ->
      call.respond(
        HttpStatusCode.PayloadTooLarge,
        ApiError(ApiErrorCode.TOO_LARGE, cause.message ?: "the request was too large"),
      )
    }
    exception<QuotaExceededException> { call, cause ->
      call.respond(
        HttpStatusCode.Forbidden,
        ApiError(ApiErrorCode.QUOTA_EXCEEDED, cause.message ?: "a per user quota was exceeded"),
      )
    }
    exception<Throwable> { call, cause ->
      logger.error("unhandled failure serving ${call.request.local.uri}", cause)
      call.respond(
        HttpStatusCode.InternalServerError,
        ApiError(ApiErrorCode.INTERNAL, "the server failed to handle the request"),
      )
    }
  }
}

/** Refuses an oversized body on its declared length, before a byte of it is read. */
private fun Application.installBodySizeGuard() {
  intercept(ApplicationCallPipeline.Plugins) {
    val declared = call.request.contentLength()
    if (declared != null && declared > MAX_BODY_BYTES) {
      call.respond(
        HttpStatusCode.PayloadTooLarge,
        ApiError(ApiErrorCode.TOO_LARGE, "the request body exceeds $MAX_BODY_BYTES bytes"),
      )
      finish()
    }
  }
}
