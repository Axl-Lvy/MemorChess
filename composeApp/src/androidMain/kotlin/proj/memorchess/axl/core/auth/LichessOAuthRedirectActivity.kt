package proj.memorchess.axl.core.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Headless activity handing the `memorchess://oauth` redirect to [PendingOAuth]. */
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
