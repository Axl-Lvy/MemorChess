package proj.memorchess.axl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import proj.memorchess.axl.ui.KoinStarterApp

lateinit var MAIN_ACTIVITY: MainActivity

class MainActivity : ComponentActivity() {
  init {
    MAIN_ACTIVITY = this
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    FileKit.init(this)
    setContent { KoinStarterApp() }
  }
}

@Preview
@Composable
fun AppAndroidPreview() {
  KoinStarterApp()
}
