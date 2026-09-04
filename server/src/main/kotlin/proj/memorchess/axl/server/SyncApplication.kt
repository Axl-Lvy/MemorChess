package proj.memorchess.axl.server

import com.auth0.jwk.JwkProvider
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentLength
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.server.auth.installJwtAuth
import proj.memorchess.axl.server.routes.syncRoutes
import proj.memorchess.axl.server.sync.SyncStore

/**
 * Largest request body accepted, checked against the declared length so nothing oversized is ever
 * buffered. A full sync batch is orders of magnitude smaller than this.
 */
internal const val MAX_BODY_BYTES: Long = 8L * 1024 * 1024

private val logger = LoggerFactory.getLogger("proj.memorchess.axl.server")

/** A request that exceeded a server side cap, whether in bytes or in row count. */
internal class TooLargeException(message: String) : Exception(message)

/**
 * Assembles the server: plugins, error mapping, probes and routes.
 *
 * Every collaborator is a parameter so a test builds the same module production does.
 *
 * @param readiness Whether the server can currently do work, answered by `/ready`.
 * @param clock Source of server time, substituted in tests that pin the skew boundary.
 */
internal fun Application.syncModule(
  config: ServerConfig,
  jwkProvider: JwkProvider,
  store: SyncStore,
  readiness: suspend () -> Boolean,
  clock: () -> Instant = Clock.System::now,
) {
  install(ContentNegotiation) { json(SYNC_JSON) }
  installErrorMapping()
  installBodySizeGuard()
  installJwtAuth(config, jwkProvider)

  routing {
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
 * Maps every failure to an [ApiError], so no stack trace, SQL text or exception class name reaches
 * a caller.
 */
private fun Application.installErrorMapping() {
  install(StatusPages) {
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
