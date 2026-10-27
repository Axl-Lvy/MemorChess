package proj.memorchess.axl.core.auth

/** OpenID Connect issuer this client authenticates against. */
const val SYNC_ISSUER: String = "https://a2qj1s.logto.app/oidc"

/**
 * API resource identifier requested as the access token audience, so the issuer returns a JWT
 * access token rather than an opaque one.
 */
const val SYNC_AUDIENCE: String = "https://api.memorchess.app"

/**
 * Client id this platform presents to the issuer. wasmJs uses a separate "Single Page App"
 * registration because the issuer enables token endpoint CORS only for that application type.
 */
expect val SYNC_CLIENT_ID: String

/** Platform specific redirect URI passed to the issuer. Same shape as [LICHESS_REDIRECT_URI]. */
expect val SYNC_REDIRECT_URI: String

/** Path portion of [SYNC_REDIRECT_URI] on wasmJs, usable before Koin starts. */
const val SYNC_REDIRECT_PATH: String = "/sync-oauth-callback"
