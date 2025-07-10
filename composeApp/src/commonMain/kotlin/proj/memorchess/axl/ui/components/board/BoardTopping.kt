package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BoardTopping(
  backGroundColor: Color,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = {},
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(42.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(backGroundColor)
        .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
  }
}
