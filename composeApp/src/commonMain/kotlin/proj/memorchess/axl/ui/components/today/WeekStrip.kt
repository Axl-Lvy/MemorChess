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

// Every dimension below is a placeholder rather than a design canvas read
// (`claude.ai/design/p/db4f236e-b602-4b5f-bcbb-a4cf70525664`), which this sandboxed environment
// could not reach. Each constant instead cites the closest existing Kinetic value it reuses, per
// the spec's own fallback rule, and should get a pass against the real artboard once reachable.

/** Size of one week-strip day cell. Not read off an artboard; see the file header note. */
private val WEEK_CELL_SIZE = 40.dp

/**
 * Gap between week-strip cells, reused from
 * [proj.memorchess.axl.ui.components.navigation.KineticSideBar]'s row gap (8.dp).
 */
private val WEEK_CELL_GAP = 8.dp

/**
 * Corner radius of a week-strip cell, reused from the bottom nav's active-pill radius (14.dp),
 * halved for a smaller element.
 */
private val WEEK_CELL_SHAPE = RoundedCornerShape(10.dp)

/**
 * Visual state of one week strip cell.
 *
 * Modeled as four states rather than the three named in the spec. [MISSED] is a distinct arm for a
 * past day with no review, and [TODAY] always wins over [DONE] even once today has been reviewed,
 * because today's own done state is already communicated by the streak badge and the goal ring, so
 * the strip does not double encode it. This is a deliberate deviation pending design confirmation.
 * If a review finds the mockup shows only three states, [MISSED] should collapse into [FUTURE]'s
 * unfilled treatment.
 */
internal enum class WeekCellState {
  DONE,
  MISSED,
  TODAY,
  FUTURE,
}

/**
 * Classifies one week strip cell at [isoIndex] (`1` Monday .. `7` Sunday), given [todayIsoIndex]
 * and whether that date counts towards the streak ([active]).
 *
 * [todayIsoIndex] is a parameter rather than a direct
 * [proj.memorchess.axl.core.date.DateUtil.today] read, so [WeekStrip] (and anything driving it) can
 * be exercised for every arm regardless of which real calendar day a test happens to run on.
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
 * Seven cells, Monday to Sunday, showing this ISO week's activity against [todayIsoIndex].
 *
 * Shared between [proj.memorchess.axl.ui.pages.Today] and the desktop rail
 * ([proj.memorchess.axl.ui.components.navigation.KineticDesktopRail]), which can both be on screen
 * at once on a wide window. [tagPrefix] keeps their two instances' test tags from colliding.
 *
 * [todayIsoIndex] is supplied by the caller rather than read from
 * [proj.memorchess.axl.core.date.DateUtil] directly, which lets a test drive every
 * [classifyWeekCell] arm through this composable for a chosen day, without a test override on
 * `DateUtil.today`. Each cell reports its classification as a semantics state description
 * (`"DONE"`/`"MISSED"`/`"TODAY"`/`"FUTURE"`) for exactly that purpose.
 *
 * @param tagPrefix Prefix for this instance's test tags: `"${tagPrefix}_strip"` on the row,
 *   `"${tagPrefix}_cell_<isoIndex>"` on each cell.
 * @param cellSize Side length of one cell. Defaults to the width Today's own full-width page uses;
 *   the desktop rail passes a smaller size so all seven cells fit its narrower column.
 * @param cellGap Gap between cells.
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
