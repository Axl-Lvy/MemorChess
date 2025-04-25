package proj.ankichess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun Router(navController: NavHostController, modifier: Modifier = Modifier) {
  NavHost(
    navController = navController,
    startDestination = Destination.TRAINING.name,
    modifier = modifier,
  ) {
    Destination.entries.forEach { destination ->
      composable(destination.name, content = destination.content)
    }
  }
}
