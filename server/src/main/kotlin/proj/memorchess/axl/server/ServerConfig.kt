package proj.memorchess.axl.server

import java.io.File
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
 * @property r2Endpoint The S3 compatible endpoint holding repertoire payload blobs.
 * @property r2Bucket Bucket name at [r2Endpoint].
 * @property r2AccessKeyId Access key id for [r2Bucket].
 * @property r2SecretAccessKey Secret access key for [r2Bucket].
 * @property adminToken Shared secret gating the admin routes. A stopgap until Cloudflare Access
 *   fronts this server. See `repertoireModule`'s KDoc for the reasoning.
 * @property staticDir Directory holding the compiled wasmJs frontend bundle, served at "/". Null
 *   disables frontend serving (local `:server:run`, tests), since only the Docker image ships a
 *   bundle to serve.
 */
internal data class ServerConfig(
  val port: Int,
  val jdbcUrl: String,
  val dbUser: String,
  val dbPassword: String,
  val jwtIssuer: String,
  val jwtAudience: String,
  val jwksUrl: URI,
  val r2Endpoint: URI,
  val r2Bucket: String,
  val r2AccessKeyId: String,
  val r2SecretAccessKey: String,
  val adminToken: String,
  val staticDir: File? = null,
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
    jwksUrl = getenv.absoluteHttpUrl("SYNC_JWKS_URL"),
    r2Endpoint = getenv.absoluteHttpUrl("SYNC_R2_ENDPOINT"),
    r2Bucket = getenv.required("SYNC_R2_BUCKET"),
    r2AccessKeyId = getenv.required("SYNC_R2_ACCESS_KEY_ID"),
    r2SecretAccessKey = getenv.required("SYNC_R2_SECRET_ACCESS_KEY"),
    adminToken = getenv.required("SYNC_ADMIN_TOKEN"),
    staticDir = getenv.optionalDir("SYNC_STATIC_DIR"),
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

private fun ((String) -> String?).absoluteHttpUrl(name: String): URI {
  val raw = required(name)
  val uri = runCatching { URI(raw) }.getOrNull()
  check(uri != null && uri.isAbsolute && uri.scheme?.lowercase() in HTTP_SCHEMES) {
    "$name must be an absolute http or https URL, was '$raw'"
  }
  return uri
}

private fun ((String) -> String?).optionalDir(name: String): File? {
  val raw = this(name)?.takeIf { it.isNotBlank() } ?: return null
  val dir = File(raw)
  check(dir.isDirectory) { "$name must be an existing directory, was '$raw'" }
  return dir
}
