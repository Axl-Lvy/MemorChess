package proj.ankichess.axl.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import kotlinx.serialization.Serializable
import proj.ankichess.axl.pages.Explore
import proj.ankichess.axl.pages.Settings
import proj.ankichess.axl.pages.Training

@Serializable
enum class Destination(val content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit) {
  TRAINING({ Training() }),
  EXPLORE({ Explore() }),
  SETTINGS({ Settings() }),
}
