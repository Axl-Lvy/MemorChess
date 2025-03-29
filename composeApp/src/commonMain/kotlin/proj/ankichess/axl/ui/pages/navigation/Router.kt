package proj.ankichess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun Router(navController: NavHostController) {
  NavHost(navController = navController, startDestination = Destination.TRAINING.name) {
    Destination.entries.forEach { destination ->
      composable(destination.name, content = destination.content)
    }
  }
}
