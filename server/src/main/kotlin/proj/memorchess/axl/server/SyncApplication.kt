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
import io.ktor.server.plugins.NotFoundException
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
import proj.memorchess.axl.server.repertoire.RepertoireStore
import proj.memorchess.axl.server.routes.repertoireModule
import proj.memorchess.axl.server.routes.syncRoutes
import proj.memorchess.axl.server.routes.versionRoute
import proj.memorchess.axl.server.sync.QuotaExceededException
import proj.memorchess.axl.server.sync.ResyncRequiredException
import proj.memorchess.axl.server.sync.SyncStore
import proj.memorchess.axl.server.sync.UnknownDeviceException

/** Largest request body accepted, checked on the declared length so nothing oversized is read. */
internal const val MAX_BODY_BYTES: Long = 8L * 1024 * 1024

private val logger = LoggerFactory.getLogger("proj.memorchess.axl.server")

/** A request that exceeded a server side cap, whether in bytes or in row count. */
internal class TooLargeException(message: String) : Exception(message)

/** One named budget: requests allowed per [refillPeriod], reset in full at each period's end. */
internal data class RateLimitTier(val limit: Int, val refillPeriod: Duration)

/**
 * Every rate limit tier the server enforces.
 *
 * @property syncWrite Authenticated writes, keyed by caller.
 * @property syncRead Authenticated pulls, keyed by caller.
 * @property publicRead Anonymous reads, keyed by [clientIp].
 * @property admin The uncredentialed moderation route, keyed by [clientIp]; it only slows callers.
 */
internal data class RateLimitTiers(
  val syncWrite: RateLimitTier,
  val syncRead: RateLimitTier,
  val publicRead: RateLimitTier,
  val admin: RateLimitTier,
)

/** The budgets every production deployment enforces. */
internal val PRODUCTION_RATE_LIMITS =
  RateLimitTiers(
    syncWrite = RateLimitTier(60, 1.minutes),
    syncRead = RateLimitTier(300, 1.minutes),
    publicRead = RateLimitTier(300, 1.minutes),
    admin = RateLimitTier(30, 1.minutes),
  )

internal val RATE_LIMIT_SYNC_WRITE = RateLimitName("sync-write")
internal val RATE_LIMIT_SYNC_READ = RateLimitName("sync-read")
internal val RATE_LIMIT_PUBLIC_READ = RateLimitName("public-read")
internal val RATE_LIMIT_ADMIN = RateLimitName("admin")

/**
 * Assembles the sync server: plugins, error mapping, probes and routes.
 *
 * @param readiness Whether the server can currently do work, answered by `/ready`.
 */
internal fun Application.syncModule(
  config: ServerConfig,
  jwkProvider: JwkProvider,
  store: SyncStore,
  readiness: suspend () -> Boolean,
  clock: () -> Instant = Clock.System::now,
  rateLimits: RateLimitTiers = PRODUCTION_RATE_LIMITS,
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

/** Mounts every module a deployment answers requests with: sync, then the repertoire catalog. */
internal fun Application.serverModules(
  config: ServerConfig,
  jwkProvider: JwkProvider,
  syncStore: SyncStore,
  repertoireStore: RepertoireStore,
  readiness: suspend () -> Boolean,
  clock: () -> Instant,
  rateLimits: RateLimitTiers,
) {
  syncModule(config, jwkProvider, syncStore, readiness, clock, rateLimits)
  repertoireModule(repertoireStore, clock, rateLimits)
}

/** Installs the named rate limit tiers unless already installed, so any module may run first. */
internal fun Application.installRateLimiting(tiers: RateLimitTiers = PRODUCTION_RATE_LIMITS) {
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

/** The authenticated caller's user id, or its IP on a route with no principal. */
private fun ApplicationCall.callerKey(): String = principal<Caller>()?.userId ?: clientIp()

/**
 * The caller's IP from Cloudflare's header, since the socket address is Cloudflare's edge.
 * Spoofable, so IP-keyed limits hold only while the origin is reachable exclusively through
 * Cloudflare.
 */
private fun ApplicationCall.clientIp(): String =
  request.header("CF-Connecting-IP") ?: request.local.remoteHost

/** Maps every failure to an [ApiError], so no stack trace or SQL text reaches a caller. */
private fun Application.installErrorMapping() {
  install(StatusPages) {
    // RateLimit answers with an empty body; rewrite it to the ApiError shape of every failure.
    status(HttpStatusCode.TooManyRequests) { call, status ->
      call.respond(status, ApiError(ApiErrorCode.RATE_LIMITED, "too many requests, slow down"))
    }
    exception<BadRequestException> { call, cause ->
      call.respond(
        HttpStatusCode.BadRequest,
        ApiError(ApiErrorCode.BAD_REQUEST, cause.message ?: "malformed request"),
      )
    }
    exception<NotFoundException> { call, cause ->
      call.respond(
        HttpStatusCode.NotFound,
        ApiError(ApiErrorCode.NOT_FOUND, cause.message ?: "no such resource"),
      )
    }
    exception<UnknownDeviceException> { call, cause ->
      call.respond(
        HttpStatusCode.BadRequest,
        ApiError(ApiErrorCode.BAD_REQUEST, cause.message ?: "unknown device"),
      )
    }
    exception<ResyncRequiredException> { call, cause ->
      call.respond(
        HttpStatusCode.Gone,
        ApiError(ApiErrorCode.RESYNC_REQUIRED, cause.message ?: "the caller must resync"),
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
