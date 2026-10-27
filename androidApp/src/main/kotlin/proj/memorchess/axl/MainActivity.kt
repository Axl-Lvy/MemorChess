package proj.memorchess.axl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import proj.memorchess.axl.ui.KoinStarterApp

/** Entry point of the Android app. */
class MainActivity : ComponentActivity() {

  @OptIn(ExperimentalComposeUiApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AndroidContextProvider.initialize(this)
    FileKit.init(this)
    setContent {
      // Lets UiAutomator in :macrobenchmark find composables by testTag.
      Box(Modifier.semantics { testTagsAsResourceId = true }) { KoinStarterApp() }
    }
  }
}
