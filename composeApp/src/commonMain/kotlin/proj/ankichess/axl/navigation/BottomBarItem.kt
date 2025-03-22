package proj.ankichess.axl.navigation

import ankichess.composeapp.generated.resources.Res
import ankichess.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.DrawableResource

enum class BottomBarItem(
  val label: String,
  val icon: DrawableResource,
  val destination: Destination,
  val index: Int,
) {
  Training(
    label = "Training",
    icon = Res.drawable.compose_multiplatform,
    destination = Destination.TRAINING,
    index = 0,
  ),
  ManageSaves(
    label = "Manage Saves",
    icon = Res.drawable.compose_multiplatform,
    destination = Destination.MANAGE_SAVES,
    index = 1,
  ),
}
