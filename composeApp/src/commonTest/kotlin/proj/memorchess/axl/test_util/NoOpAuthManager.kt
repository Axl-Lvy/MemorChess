package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.online.auth.AuthManager

class NoOpAuthManager : AuthManager {
  override val user = null

  override suspend fun signInFromEmail(providedEmail: String, providedPassword: String) {
    // Do nothing
  }

  override suspend fun signUpFromEmail(providedEmail: String, providedPassword: String) {
    // Do nothing
  }

  override suspend fun confirmEmail(email: String, token: String) {
    // Do nothing
  }

  override suspend fun signOut() {
    // Do nothing
  }

  override fun updateSavedTokens() {
    // Do nothing
  }
}
