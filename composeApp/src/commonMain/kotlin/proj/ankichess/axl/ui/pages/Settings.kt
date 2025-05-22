package proj.ankichess.axl.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import proj.ankichess.axl.ui.pages.navigation.Destination

@Composable
fun Settings() {
  Column(
    verticalArrangement = Arrangement.Center,
    modifier = Modifier.testTag(Destination.SETTINGS.name),
  ) {
    Text(text = "Settings")
  }
}
