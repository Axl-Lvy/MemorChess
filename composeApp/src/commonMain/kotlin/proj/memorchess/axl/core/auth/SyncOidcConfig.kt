package proj.memorchess.axl.core.auth

/**
 * OpenID Connect issuer this client authenticates against. Configuration, not a vendor named in
 * code: swapping identity vendors means changing this one value and the dashboard behind it.
 */
const val SYNC_ISSUER: String = "https://a2qj1s.logto.app/oidc"

/**
 * API resource identifier requested as the access token audience, so the issuer returns a JWT
 * access token rather than an opaque one (most OIDC vendors default to opaque without this).
 */
const val SYNC_AUDIENCE: String = "https://api.memorchess.app"

/**
 * Client id this platform presents to the issuer. Android, iOS and JVM share one public "Native"
 * app; wasmJs uses a separate "Single Page App" registration because the issuer enables CORS on its
 * token endpoint only for that application type, which a browser `fetch` call needs.
 */
expect val SYNC_CLIENT_ID: String

/** Platform specific redirect URI passed to the issuer. Same shape as [LICHESS_REDIRECT_URI]. */
expect val SYNC_REDIRECT_URI: String
