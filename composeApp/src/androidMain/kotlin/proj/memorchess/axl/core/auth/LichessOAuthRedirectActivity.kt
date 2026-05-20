package proj.memorchess.axl.core.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Headless activity registered against the `memorchess://oauth` scheme.
 *
 * Receives the redirect URI from the Custom Tab, hands it to [PendingOAuth], then finishes so the
 * MainActivity comes back to the foreground.
 */
class LichessOAuthRedirectActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)
    finish()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
    finish()
  }

  private fun handleIntent(intent: Intent?) {
    val data = intent?.data ?: return PendingOAuth.cancel()
    PendingOAuth.complete(data)
  }
}
