package proj.ankichess.axl.ui.pages.navigation.bottomBar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ankichess.composeapp.generated.resources.Res
import ankichess.composeapp.generated.resources.icon_main
import org.jetbrains.compose.resources.painterResource
import proj.ankichess.axl.ui.pages.navigation.Destination

@Composable
fun CenterButton(navController: NavHostController) {
  FloatingActionButton(onClick = { navController.navigate(Destination.TRAINING.name) }) {
    Icon(
      modifier = Modifier.size(64.dp).padding(10.dp),
      painter = painterResource(Res.drawable.icon_main),
      contentDescription = Destination.TRAINING.name + " button",
    )
  }
}
