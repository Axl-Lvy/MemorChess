package proj.ankichess.axl.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import proj.ankichess.axl.pages.ManageSaves
import proj.ankichess.axl.pages.Training

@Composable
fun Router(navController: NavHostController) {
  NavHost(navController = navController, startDestination = Destination.TRAINING.name) {
    composable(Destination.TRAINING.name) { Training() }
    composable(Destination.EXPLORE.name) { ManageSaves() }
  }
}
