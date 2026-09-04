package proj.memorchess.axl.server.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import java.net.URI
import java.util.concurrent.TimeUnit
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.server.ServerConfig

/** Name of the only authentication provider, referenced by every protected route. */
internal const val SYNC_AUTH: String = "sync-jwt"

/**
 * Longest `sub` accepted. Providers stay well under this; the cap exists so a forged token cannot
 * make the server key rows on an unbounded string.
 */
private const val MAX_SUBJECT_LENGTH = 255

/** How many keys the JWKS cache holds. An issuer publishes one or two, plus one mid rotation. */
private const val JWKS_CACHE_SIZE = 10L

/** How long a cached key stays usable before it is refetched. */
private const val JWKS_CACHE_TTL_MINUTES = 10L

/** Ceiling on JWKS fetches, so an unknown `kid` cannot be used to hammer the issuer. */
private const val JWKS_FETCHES_PER_MINUTE = 10L

/**
 * The authenticated caller.
 *
 * @property userId The token's `sub`. Every per user row is keyed by this and by nothing the caller
 *   sent in a body or a query parameter.
 */
internal data class Caller(val userId: String)

/**
 * Installs bearer token authentication under [SYNC_AUTH].
 *
 * `iss`, `aud`, `exp` and the signature are all checked, and no clock leeway is granted.
 */
internal fun Application.installJwtAuth(config: ServerConfig, jwkProvider: JwkProvider) {
  install(Authentication) {
    jwt(SYNC_AUTH) {
      verifier(jwkProvider, config.jwtIssuer) {
        withAudience(config.jwtAudience)
        acceptLeeway(0)
      }
      validate { credential ->
        credential.subject
          ?.takeIf { it.isNotBlank() && it.length <= MAX_SUBJECT_LENGTH }
          ?.let(::Caller)
      }
      challenge { _, _ ->
        call.respond(
          HttpStatusCode.Unauthorized,
          ApiError(ApiErrorCode.UNAUTHORIZED, "a valid bearer token is required"),
        )
      }
    }
  }
}

/**
 * The caller's user id.
 *
 * @throws IllegalArgumentException when read outside a route wrapped in [SYNC_AUTH].
 */
internal val ApplicationCall.callerId: String
  get() =
    requireNotNull(principal<Caller>()) {
        "callerId is only available inside an authenticated route"
      }
      .userId

/**
 * The production key source: cached with a TTL, refetched on an unknown `kid`, and rate limited.
 */
internal fun jwksProvider(url: URI): JwkProvider =
  JwkProviderBuilder(url.toURL())
    .cached(JWKS_CACHE_SIZE, JWKS_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
    .rateLimited(JWKS_FETCHES_PER_MINUTE, 1, TimeUnit.MINUTES)
    .build()
