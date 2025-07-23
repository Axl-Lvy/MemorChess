package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.pages.Settings
import proj.memorchess.axl.ui.pages.Training

/**
 * Represents a route.
 *
 * @property label How it should be displayed in the navigation bar
 * @property content Content of the destination
 */
@Serializable
enum class Destination(
  val label: String,
  val content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
  @SerialName("Training") TRAINING("Training", { Training() }),
  @SerialName("Explore") EXPLORE("Explore", { Explore() }),
  @SerialName("Settings") SETTINGS("Settings", { Settings() }),
}
