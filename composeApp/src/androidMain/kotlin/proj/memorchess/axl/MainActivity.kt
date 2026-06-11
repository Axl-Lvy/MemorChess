package proj.memorchess.axl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import proj.memorchess.axl.ui.KoinStarterApp

/** Entry point of the Android app. Wires platform initialization then composes the shared UI. */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AndroidContextProvider.initialize(this)
    FileKit.init(this)
    setContent { KoinStarterApp() }
  }
}
