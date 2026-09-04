package proj.memorchess.axl.server

import java.net.URI

/** Port used when `SYNC_PORT` is absent. */
private const val DEFAULT_PORT = 8080

private val HTTP_SCHEMES = setOf("http", "https")

/**
 * Everything the server needs from its environment, already validated.
 *
 * @property port TCP port to bind.
 * @property jdbcUrl Postgres JDBC URL.
 * @property dbUser Postgres role.
 * @property dbPassword Password for [dbUser].
 * @property jwtIssuer Expected `iss` claim, compared exactly.
 * @property jwtAudience Expected `aud` claim, compared exactly.
 * @property jwksUrl Where the issuer publishes its signing keys.
 */
internal data class ServerConfig(
  val port: Int,
  val jdbcUrl: String,
  val dbUser: String,
  val dbPassword: String,
  val jwtIssuer: String,
  val jwtAudience: String,
  val jwksUrl: URI,
)

/**
 * Builds the configuration, failing before anything binds or connects.
 *
 * @param getenv Environment reader, substituted in tests.
 * @throws IllegalStateException naming the offending variable.
 */
internal fun serverConfigFromEnv(getenv: (String) -> String? = System::getenv): ServerConfig =
  ServerConfig(
    port = getenv.port(),
    jdbcUrl = getenv.required("SYNC_DB_URL"),
    dbUser = getenv.required("SYNC_DB_USER"),
    dbPassword = getenv.required("SYNC_DB_PASSWORD"),
    jwtIssuer = getenv.required("SYNC_JWT_ISSUER"),
    jwtAudience = getenv.required("SYNC_JWT_AUDIENCE"),
    jwksUrl = getenv.jwksUrl(),
  )

private fun ((String) -> String?).required(name: String): String {
  val value = this(name)
  check(!value.isNullOrBlank()) { "$name is required and must not be blank" }
  return value
}

private fun ((String) -> String?).port(): Int {
  val raw = this("SYNC_PORT")?.takeIf { it.isNotBlank() } ?: return DEFAULT_PORT
  val port = raw.toIntOrNull()
  check(port != null && port in 1..65535) { "SYNC_PORT must be a port number, was '$raw'" }
  return port
}

private fun ((String) -> String?).jwksUrl(): URI {
  val raw = required("SYNC_JWKS_URL")
  val uri = runCatching { URI(raw) }.getOrNull()
  check(uri != null && uri.isAbsolute && uri.scheme?.lowercase() in HTTP_SCHEMES) {
    "SYNC_JWKS_URL must be an absolute http or https URL, was '$raw'"
  }
  return uri
}
