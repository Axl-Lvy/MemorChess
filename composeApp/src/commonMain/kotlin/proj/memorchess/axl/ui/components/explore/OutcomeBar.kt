package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette

// Piece colours rather than Kinetic tokens: they read the same in both themes and carry no
// good/bad meaning for either side.
private val WHITE_WINS = Color(0xFFF2EFE8)
private val DRAWS = Color(0xFF8C8A93)
private val BLACK_WINS = Color(0xFF1F1B24)

private const val MIN_WEIGHT = 0.0001f

/**
 * Stacked white wins / draws / black wins bar in neutral piece colours. With no games it shows only
 * the empty track.
 */
@Composable
internal fun OutcomeBar(white: Long, draws: Long, black: Long, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val total = white.toDouble() + draws.toDouble() + black.toDouble()
  Row(modifier = modifier.background(palette.panel3).border(width = 1.dp, color = palette.line)) {
    Segment(count = white, total = total, color = WHITE_WINS, tag = "outcome_bar_white")
    Segment(count = draws, total = total, color = DRAWS, tag = "outcome_bar_draws")
    Segment(count = black, total = total, color = BLACK_WINS, tag = "outcome_bar_black")
  }
}

@Composable
private fun RowScope.Segment(count: Long, total: Double, color: Color, tag: String) {
  // `weight(0f)` throws, so an outcome with no games draws nothing.
  if (count <= 0) return
  val weight = (count / total).toFloat().coerceAtLeast(MIN_WEIGHT)
  Box(modifier = Modifier.weight(weight).fillMaxHeight().background(color).testTag(tag))
}
