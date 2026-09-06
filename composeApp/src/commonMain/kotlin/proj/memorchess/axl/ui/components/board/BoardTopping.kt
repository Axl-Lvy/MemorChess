package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Full-width 42.dp strip sitting above the board column, used by
 * [proj.memorchess.axl.ui.components.board.StateIndicator] to tint the current position's state.
 *
 * The strip rounds to `MaterialTheme.shapes.extraSmall` (12.dp), the in-card / chip bucket of the
 * Kinetic shape contract: it is a strip inside the board column, not a card of its own, so it does
 * not take the 20.dp panel radius. The `.clip` is what keeps the icon + label [content] row off
 * those corners.
 *
 * @param backGroundColor Fill of the strip. Supplied by the caller as a state-derived tint (the
 *   node state's colour at low alpha), deliberately not a palette token read here.
 * @param modifier External modifier applied to the strip.
 * @param content Row content centred inside the strip, typically an icon and a label.
 */
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
        .clip(MaterialTheme.shapes.extraSmall)
        .background(backGroundColor)
        .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
  }
}
