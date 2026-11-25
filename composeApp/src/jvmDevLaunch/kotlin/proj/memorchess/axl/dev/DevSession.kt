package proj.memorchess.axl.dev

import java.io.File
import java.util.Properties
import kotlin.time.Instant
import proj.memorchess.axl.core.auth.AccessToken
import proj.memorchess.axl.core.auth.Account

/** Identifies one run of the local dev server; a new value means the server started empty. */
@JvmInline internal value class DevServerGeneration(val value: String)

/** The local dev server's address and the dev user's session, as `:server:devRun` wrote them. */
internal data class DevSession(
  val baseUrl: String,
  val accessToken: AccessToken,
  val expiresAt: Instant,
  val account: Account,
  val generation: DevServerGeneration,
)

private const val DEV_USER_NAME = "Dev user"

/**
 * Reads the session file the local dev server writes, or returns `null` when there is none.
 *
 * @throws IllegalStateException naming the missing key when the file is incomplete.
 */
internal fun readDevSession(file: File?): DevSession? {
  if (file == null || !file.isFile) return null
  val properties = Properties().apply { file.inputStream().use(::load) }
  fun required(key: String): String =
    properties.getProperty(key) ?: error("Dev session file $file has no '$key'")
  return DevSession(
    baseUrl = required("baseUrl"),
    accessToken = AccessToken(required("accessToken")),
    expiresAt = Instant.parse(required("expiresAt")),
    account = Account(required("sub"), DEV_USER_NAME),
    generation = DevServerGeneration(required("generation")),
  )
}

/** Returns what to tell the developer when [session] has expired at [now], or `null` if not. */
internal fun expiredSessionHint(session: DevSession?, now: Instant): String? =
  if (session != null && now >= session.expiresAt) {
    "Dev session expired: restart ./gradlew :server:devRun"
  } else {
    null
  }
