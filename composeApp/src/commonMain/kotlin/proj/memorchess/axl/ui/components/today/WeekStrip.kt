package proj.memorchess.axl.ui.components.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.today_weekday_friday
import memorchess.composeapp.generated.resources.today_weekday_monday
import memorchess.composeapp.generated.resources.today_weekday_saturday
import memorchess.composeapp.generated.resources.today_weekday_sunday
import memorchess.composeapp.generated.resources.today_weekday_thursday
import memorchess.composeapp.generated.resources.today_weekday_tuesday
import memorchess.composeapp.generated.resources.today_weekday_wednesday
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private val WEEK_CELL_SIZE = 40.dp

private val WEEK_CELL_GAP = 8.dp

private val WEEK_CELL_SHAPE = RoundedCornerShape(10.dp)

/**
 * Visual state of one week strip cell. [TODAY] wins over [DONE], since the streak badge and goal
 * ring already show whether today was reviewed.
 */
internal enum class WeekCellState {
  DONE,
  MISSED,
  TODAY,
  FUTURE,
}

/**
 * Classifies the week strip cell at [isoIndex] (`1` Monday .. `7` Sunday); [active] means that date
 * counts towards the streak.
 */
internal fun classifyWeekCell(isoIndex: Int, todayIsoIndex: Int, active: Boolean): WeekCellState =
  when {
    isoIndex == todayIsoIndex -> WeekCellState.TODAY
    isoIndex > todayIsoIndex -> WeekCellState.FUTURE
    active -> WeekCellState.DONE
    else -> WeekCellState.MISSED
  }

/** The localized weekday name for ISO day number [isoDayNumber] (`1` Monday .. `7` Sunday). */
internal fun weekdayNameRes(isoDayNumber: Int): StringResource =
  when (isoDayNumber) {
    1 -> Res.string.today_weekday_monday
    2 -> Res.string.today_weekday_tuesday
    3 -> Res.string.today_weekday_wednesday
    4 -> Res.string.today_weekday_thursday
    5 -> Res.string.today_weekday_friday
    6 -> Res.string.today_weekday_saturday
    else -> Res.string.today_weekday_sunday
  }

/**
 * Seven cells, Monday to Sunday, showing this ISO week's activity against [todayIsoIndex]. Each
 * cell exposes its [WeekCellState] name as its semantics state description.
 *
 * @param tagPrefix Prefix of the `_strip` and `_cell_<isoIndex>` test tags; must differ between
 *   instances on screen at once.
 */
@Composable
internal fun WeekStrip(
  week: List<Boolean>,
  todayIsoIndex: Int,
  tagPrefix: String = "today_week",
  cellSize: Dp = WEEK_CELL_SIZE,
  cellGap: Dp = WEEK_CELL_GAP,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_strip"),
    horizontalArrangement = Arrangement.spacedBy(cellGap),
  ) {
    for (isoIndex in 1..7) {
      val state = classifyWeekCell(isoIndex, todayIsoIndex, week[isoIndex - 1])
      val background =
        when (state) {
          WeekCellState.DONE -> palette.progress
          WeekCellState.TODAY -> palette.action
          WeekCellState.MISSED -> palette.panel3
          WeekCellState.FUTURE -> palette.panel2
        }
      val content =
        when (state) {
          WeekCellState.DONE -> palette.onProgress
          WeekCellState.TODAY -> palette.onAction
          WeekCellState.MISSED -> palette.ink3
          WeekCellState.FUTURE -> palette.ink4
        }
      Box(
        modifier =
          Modifier.size(cellSize)
            .testTag("${tagPrefix}_cell_$isoIndex")
            .semantics { stateDescription = state.name }
            .clip(WEEK_CELL_SHAPE)
            .background(background),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(weekdayNameRes(isoIndex)).take(1),
          style = typography.label.copy(color = content),
        )
      }
    }
  }
}
