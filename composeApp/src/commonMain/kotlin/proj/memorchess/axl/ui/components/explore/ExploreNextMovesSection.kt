package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExploreNextMovesSection(
  modifier: Modifier = Modifier,
  nextMoveButtons: @Composable () -> List<@Composable () -> Unit>,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    val moves = nextMoveButtons()
    if (moves.isNotEmpty()) {
      LazyRow(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(vertical = 8.dp),
      ) {
        itemsIndexed(
          moves,
          itemContent = { index, moveButton ->
            Box(
              modifier = Modifier.fillMaxHeight().width(48.dp),
              contentAlignment = Alignment.Center,
            ) {
              moveButton()
            }
            if (index < moves.lastIndex) {
              Box(
                Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary)
              )
            }
          },
        )
      }
    } else {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer)
      ) {
        Text("No next moves", modifier = Modifier.align(Alignment.Center))
      }
    }
  }
}
