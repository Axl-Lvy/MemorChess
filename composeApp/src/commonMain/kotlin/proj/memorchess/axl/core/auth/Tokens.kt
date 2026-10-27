package proj.memorchess.axl.core.auth

import kotlin.jvm.JvmInline

/** Bearer token for this app's own sync and publish API. Its [toString] never shows the secret. */
@JvmInline
value class AccessToken(val value: String) {
  override fun toString(): String = "AccessToken(redacted)"
}

/** Token the OIDC issuer exchanges for a fresh [AccessToken]. Its [toString] never shows it. */
@JvmInline
value class RefreshToken(val value: String) {
  override fun toString(): String = "RefreshToken(redacted)"
}

/** Bearer token for the Lichess API. Its [toString] never shows the secret. */
@JvmInline
value class LichessToken(val value: String) {
  override fun toString(): String = "LichessToken(redacted)"
}
