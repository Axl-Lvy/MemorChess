package proj.memorchess.axl.core.data.online.auth

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo

interface AuthManager {
  val user: UserInfo?

  /**
   * Sign in from email.
   *
   * @param providedEmail email
   * @param providedPassword password
   */
  suspend fun signInFromEmail(providedEmail: String, providedPassword: String)

  suspend fun signOut()

  fun updateSavedTokens()

  fun registerListener(listener: suspend (SessionStatus) -> Unit)
}
