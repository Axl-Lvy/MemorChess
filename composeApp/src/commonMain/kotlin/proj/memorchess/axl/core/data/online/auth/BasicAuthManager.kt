package proj.memorchess.axl.core.data.online.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

/**
 * A singleton class for managing Supabase authentication
 *
 * @property supabaseClient
 * @constructor Create empty Supabase auth manager
 */
class BasicAuthManager(private val supabaseClient: SupabaseClient) : AuthManager {

  /** The authenticated user. This class ensures this state is always up to date. */
  override var user by mutableStateOf(supabaseClient.auth.currentUserOrNull())
    private set

  /** Refresh [user] */
  private fun refreshUser() {
    user = supabaseClient.auth.currentUserOrNull()
  }

  /**
   * Sign in from email.
   *
   * @param providedEmail email
   * @param providedPassword password
   */
  override suspend fun signInFromEmail(providedEmail: String, providedPassword: String) {
    supabaseClient.auth.signInWith(Email) {
      email = providedEmail
      password = providedPassword
    }
    refreshUser()
  }

  override suspend fun signUpFromEmail(providedEmail: String, providedPassword: String) {
    supabaseClient.auth.signUpWith(Email) {
      email = providedEmail
      password = providedPassword
    }
    refreshUser()
  }

  override suspend fun confirmEmail(email: String, token: String) {
    supabaseClient.auth.verifyEmailOtp(OtpType.Email.MAGIC_LINK, token)
  }

  override suspend fun signOut() {
    supabaseClient.auth.signOut()
    refreshUser()
  }
}
