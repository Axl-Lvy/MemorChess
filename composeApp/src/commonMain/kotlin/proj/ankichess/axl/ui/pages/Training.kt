package proj.ankichess.axl.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import proj.ankichess.axl.ui.pages.navigation.Destination

@Composable
fun Training() {
  Text("Training", Modifier.fillMaxWidth().testTag(Destination.TRAINING.name))
}
