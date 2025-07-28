package proj.memorchess.axl.ui.layout.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LandscapeTrainingLayout(
  modifier: Modifier = Modifier,
  builder: TrainingLayoutContent.() -> Unit,
) {
  val content = TrainingLayoutContent()
  builder.invoke(content)

  Row(
    horizontalArrangement = Arrangement.spacedBy(5.dp, alignment = Alignment.CenterHorizontally),
    modifier = modifier.fillMaxHeight(),
  ) {
    BoxWithConstraints {
      if (maxHeight > maxWidth) content.board(Modifier.fillMaxWidth())
      else content.board(Modifier.fillMaxHeight())
    }
    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
        content.movesToTrain(Modifier.fillMaxWidth())
      }
      Spacer(modifier = Modifier.height(32.dp))
      Box(modifier = Modifier.weight(1f)) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceEvenly,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          content.daysInAdvance(Modifier.weight(1f).align(Alignment.CenterHorizontally))
          content.successIndicator(Modifier.weight(1f).fillMaxWidth())
          Spacer(modifier = Modifier.height(32.dp))
        }
      }
    }
  }
}
