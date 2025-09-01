package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route

@Composable
fun SuccessIndicatorCard(
  isCorrect: Boolean,
  isVisible: Boolean,
  nextMove: () -> Unit,
  failedPosition: PositionIdentifier?,
  modifier: Modifier = Modifier.Companion,
  navigator: Navigator = koinInject(),
) {
  if (!isVisible) {
    return
  }

  val backgroundColor =
    if (isCorrect) {
      Color(0xFF4CAF50).copy(alpha = 0.1f)
    } else {
      Color(0xFFF44336).copy(alpha = 0.1f)
    }

  val iconColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
  val icon = if (isCorrect) Icons.Default.Done else Icons.Default.Close

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = backgroundColor),
  ) {
    if (isCorrect) {
      CorrectIcon(icon, isCorrect, iconColor)
    } else {

      Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
      ) {
        Row {
          IconButton(
            onClick = {
              checkNotNull(failedPosition) {
                "Failed position must not be null when displaying an incorrect move."
              }
              navigator.navigateTo(Route.ExploreRoute(position = failedPosition.fenRepresentation))
            },
            modifier = Modifier.weight(1f).padding(8.dp).testTag("Go to explore"),
          ) {
            Icon(Icons.Default.Search, contentDescription = "Go to explore")
          }
          CorrectIcon(icon, isCorrect, iconColor)
          IconButton(
            onClick = nextMove,
            modifier = Modifier.weight(1f).padding(8.dp).testTag("Next node"),
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Next move")
          }
        }
      }
    }
  }
}

@Composable
private fun CorrectIcon(icon: ImageVector, isCorrect: Boolean, iconColor: Color) {
  Icon(
    imageVector = icon,
    contentDescription = if (isCorrect) "Correct" else "Incorrect",
    tint = iconColor,
    modifier = Modifier.size(32.dp),
  )
}
