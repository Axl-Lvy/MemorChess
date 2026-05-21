package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * One displayable move in the [MovesTrail].
 *
 * @property san Standard Algebraic Notation of the move (e.g. `"Nf3"`).
 * @property moveNumber 1-indexed full-move number. Combined with [isWhiteMove] to render `"1."` or
 *   `"1..."` before the SAN.
 * @property isGuess `true` when this move is the user's pending guess and should be rendered with a
 *   dashed border and a trailing `" ?"`.
 * @property isWhiteMove `true` for a white ply, `false` for a black ply.
 */
data class MoveDisplay(
  val san: String,
  val moveNumber: Int,
  val isGuess: Boolean = false,
  val isWhiteMove: Boolean,
)

private val TrailHeight = 36.dp
private val ChipHorizontalPadding = 7.dp
private val ChipVerticalPadding = 3.dp
private val ChipSpacing = 4.dp
private val EdgeFadeWidth = 16.dp
private val ArrowSize = 24.dp

/**
 * Horizontal "moves trail" — Kinetic counterpart of `.trail` in
 * `design-proposals/kinetic-base.css`.
 *
 * Layout, left to right:
 * 1. **Opening pin** ([openingName]) — only rendered when non-null. Uppercase mono 9.5sp on
 *    `accentSoft` background with `accentText` text, padded 12.dp horizontally.
 * 2. **Scrollable chip strip** — a [LazyRow] of [MoveDisplay] chips on a `bg2` background. Each
 *    chip carries a 1.dp `line` border and `bg` background by default, rendering the SAN in mono
 *    11sp prefixed with the move number (`"1. Nf3"` / `"1... Nf6"`). Items are separated by 4.dp.
 *    The strip fades its leftmost and rightmost ~16.dp into the trail background via a
 *    `drawWithContent` gradient overlay so chips dissolve at the boundary instead of butting
 *    against the arrows.
 *     - Current move ([currentIndex] == index): solid `accent` background with `onAccent` text and
 *       `accent` border.
 *     - Guess move ([MoveDisplay.isGuess]): dashed `ink4` border drawn via [drawBehind] with a
 *       dashed [PathEffect], and the SAN gets a trailing `" ?"`.
 *
 *   Whenever [currentIndex] changes the strip auto-scrolls so the current chip is visible.
 * 3. **Left/right arrows** — `◀ ▶` 24.dp icon buttons that page the scroll by roughly one viewport
 *    in each direction.
 * 4. **PGN button** — only rendered when [pgnText] is non-null. Opens a dismissable [Dialog] with a
 *    scrollable two-column move list (white / black) styled on `panel` with a 1.dp `line` border,
 *    mirroring the `.pgn-overlay`/`.pgn-card` rule set.
 *
 * The whole container is 36.dp tall with a `bg2` background and a 1.dp `line` bottom border.
 *
 * @param moves The full ply list, in playing order.
 * @param currentIndex Index of the current move, or `-1` for no selection.
 * @param onSeek Invoked with the chip's index when the user taps a chip.
 * @param openingName Optional opening label rendered in the pinned left badge.
 * @param pgnText Optional raw PGN string shown in the overlay. The overlay button is only shown
 *   when this is non-null.
 */
@Composable
fun MovesTrail(
  moves: List<MoveDisplay>,
  currentIndex: Int,
  onSeek: (Int) -> Unit,
  modifier: Modifier = Modifier,
  openingName: String? = null,
  pgnText: String? = null,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var pgnOpen by remember { mutableStateOf(false) }

  // Auto-snap to the current chip when the selection changes. Skips negative / out-of-range
  // indices to avoid a `LazyList` crash on empty trails.
  LaunchedEffect(currentIndex, moves.size) {
    if (currentIndex in moves.indices) {
      listState.animateScrollToItem(currentIndex)
    }
  }

  Row(
    modifier =
      modifier.fillMaxWidth().height(TrailHeight).background(palette.bg2).drawBehind {
        // 1.dp bottom border in `line` (matches `.trail` `border-bottom`).
        val strokePx = 1.dp.toPx()
        drawLine(
          color = palette.line,
          start = Offset(0f, size.height - strokePx / 2f),
          end = Offset(size.width, size.height - strokePx / 2f),
          strokeWidth = strokePx,
        )
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (openingName != null) {
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .background(palette.accentSoft)
            .border(width = 1.dp, color = palette.line)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = openingName.uppercase(),
          style = typography.monoSm.copy(fontSize = 9.5.sp, color = palette.accentText),
        )
      }
    }

    TrailArrow(
      onClick = {
        scope.launch {
          val target = (listState.firstVisibleItemIndex - 4).coerceAtLeast(0)
          listState.animateScrollToItem(target)
        }
      },
      isLeft = true,
    )

    Box(
      modifier =
        Modifier.weight(1f).fillMaxHeight().drawWithContent {
          drawContent()
          val fadePx = EdgeFadeWidth.toPx().coerceAtMost(size.width / 2f)
          if (fadePx > 0f) {
            // Left fade: bg2 -> transparent
            drawRect(
              brush =
                Brush.horizontalGradient(
                  colors = listOf(palette.bg2, Color.Transparent),
                  startX = 0f,
                  endX = fadePx,
                ),
              topLeft = Offset(0f, 0f),
              size = Size(fadePx, size.height),
            )
            // Right fade: transparent -> bg2
            drawRect(
              brush =
                Brush.horizontalGradient(
                  colors = listOf(Color.Transparent, palette.bg2),
                  startX = size.width - fadePx,
                  endX = size.width,
                ),
              topLeft = Offset(size.width - fadePx, 0f),
              size = Size(fadePx, size.height),
            )
          }
        },
      contentAlignment = Alignment.CenterStart,
    ) {
      LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = EdgeFadeWidth),
        modifier = Modifier.fillMaxWidth(),
      ) {
        itemsIndexed(moves) { index, move ->
          MoveChip(move = move, isCurrent = index == currentIndex, onClick = { onSeek(index) })
        }
      }
    }

    TrailArrow(
      onClick = {
        scope.launch {
          val lastVisible =
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
              ?: listState.firstVisibleItemIndex
          val target = (lastVisible + 4).coerceAtMost((moves.size - 1).coerceAtLeast(0))
          if (moves.isNotEmpty()) listState.animateScrollToItem(target)
        }
      },
      isLeft = false,
    )

    if (pgnText != null) {
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .border(width = 1.dp, color = palette.line)
            .clickable { pgnOpen = true }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = "PGN", style = typography.monoSm.copy(fontSize = 9.5.sp, color = palette.ink3))
      }
    }
  }

  if (pgnText != null && pgnOpen) {
    PgnDialog(pgnText = pgnText, moves = moves, currentIndex = currentIndex) { pgnOpen = false }
  }
}

/** Single move chip — renders one ply with its number prefix, current/guess states, and click. */
@Composable
private fun MoveChip(move: MoveDisplay, isCurrent: Boolean, onClick: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  val background = if (isCurrent) palette.accent else palette.bg
  val borderColor =
    when {
      isCurrent -> palette.accent
      move.isGuess -> palette.ink4
      else -> palette.line
    }
  val contentColor = if (isCurrent) palette.onAccent else palette.ink2

  // Dashed border for guess chips: drawn with a dashed PathEffect via drawBehind so the dash
  // pattern matches `.trail-mv.guess { border-style: dashed }`.
  val borderModifier =
    if (move.isGuess && !isCurrent) {
      Modifier.drawBehind {
        val strokePx = 1.dp.toPx()
        drawRect(
          color = borderColor,
          topLeft = Offset(strokePx / 2f, strokePx / 2f),
          size = Size(size.width - strokePx, size.height - strokePx),
          style =
            Stroke(
              width = strokePx,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f),
            ),
        )
      }
    } else {
      Modifier.border(width = 1.dp, color = borderColor)
    }

  val prefix = if (move.isWhiteMove) "${move.moveNumber}." else "${move.moveNumber}..."
  val suffix = if (move.isGuess) " ?" else ""
  val label = "$prefix ${move.san}$suffix"

  Box(
    modifier =
      Modifier.background(background)
        .then(borderModifier)
        .clickable(onClick = onClick)
        .padding(horizontal = ChipHorizontalPadding, vertical = ChipVerticalPadding),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = typography.mono.copy(fontSize = 11.sp, color = contentColor),
      fontWeight = if (isCurrent || move.isGuess) FontWeight.SemiBold else FontWeight.Medium,
    )
  }
}

/** Small 24.dp arrow button used on either side of the scroll strip. */
@Composable
private fun TrailArrow(onClick: () -> Unit, isLeft: Boolean) {
  val palette = LocalKineticPalette.current
  Box(
    modifier =
      Modifier.fillMaxHeight()
        .width(ArrowSize)
        .background(palette.bg2)
        .border(width = 1.dp, color = palette.line)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    // Render a simple Unicode glyph rather than pulling in a vector resource — keeps this
    // component self-contained and matches the `◀ ▶` characters cited in the brief.
    Text(text = if (isLeft) "◀" else "▶", style = TextStyle(fontSize = 10.sp, color = palette.ink3))
  }
}

/**
 * PGN overlay dialog. Mirrors `.pgn-overlay` / `.pgn-card`: panel background, 1.dp line border,
 * scrollable two-column grid (move number, white ply, black ply). The current move is highlighted
 * in `accentText`.
 */
@Composable
private fun PgnDialog(
  pgnText: String,
  moves: List<MoveDisplay>,
  currentIndex: Int,
  onDismiss: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  // Pair the moves into (white, black) rows keyed by full-move number so the grid lines up even
  // when the trail starts on black or contains gaps.
  val rows =
    remember(moves) {
      val grouped = moves.withIndex().groupBy { it.value.moveNumber }
      grouped.entries
        .sortedBy { it.key }
        .map { (number, entries) ->
          val white = entries.firstOrNull { it.value.isWhiteMove }
          val black = entries.firstOrNull { !it.value.isWhiteMove }
          PgnRow(
            number = number,
            white = white?.value,
            whiteIndex = white?.index ?: -1,
            black = black?.value,
            blackIndex = black?.index ?: -1,
          )
        }
    }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties()) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(max = 600.dp)
          .background(palette.panel)
          .border(width = 1.dp, color = palette.line)
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(palette.panel)
            .drawBehind {
              val strokePx = 1.dp.toPx()
              drawLine(
                color = palette.line,
                start = Offset(0f, size.height - strokePx / 2f),
                end = Offset(size.width, size.height - strokePx / 2f),
                strokeWidth = strokePx,
              )
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(text = "PGN", style = typography.display.copy(color = palette.ink))
        TextButton(onClick = onDismiss) {
          Text(text = "Close", style = typography.monoSm.copy(color = palette.ink2))
        }
      }

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
      ) {
        if (rows.isEmpty()) {
          Text(text = pgnText, style = typography.mono.copy(fontSize = 13.sp, color = palette.ink2))
        } else {
          rows.forEach { row -> PgnRowView(row = row, currentIndex = currentIndex) }
        }
      }
    }
  }
}

/** Internal data carrier for a row of the PGN overlay. */
private data class PgnRow(
  val number: Int,
  val white: MoveDisplay?,
  val whiteIndex: Int,
  val black: MoveDisplay?,
  val blackIndex: Int,
)

/** One row of the PGN overlay — number, white ply, black ply, separated by a 1.dp `line`. */
@Composable
private fun PgnRowView(row: PgnRow, currentIndex: Int) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val rowStyle = typography.mono.copy(fontSize = 13.sp, color = palette.ink2)

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .drawBehind {
          val strokePx = 1.dp.toPx()
          drawLine(
            color = palette.line,
            start = Offset(0f, size.height - strokePx / 2f),
            end = Offset(size.width, size.height - strokePx / 2f),
            strokeWidth = strokePx,
          )
        }
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.width(36.dp)) {
      Text(text = "${row.number}.", style = rowStyle.copy(color = palette.ink4))
    }
    PlyCell(
      modifier = Modifier.weight(1f),
      move = row.white,
      isCurrent = row.whiteIndex == currentIndex && row.whiteIndex >= 0,
      baseStyle = rowStyle,
    )
    PlyCell(
      modifier = Modifier.weight(1f),
      move = row.black,
      isCurrent = row.blackIndex == currentIndex && row.blackIndex >= 0,
      baseStyle = rowStyle,
    )
  }
}

/** Single ply cell within a PGN row. Renders an em dash for missing plies. */
@Composable
private fun PlyCell(
  modifier: Modifier,
  move: MoveDisplay?,
  isCurrent: Boolean,
  baseStyle: TextStyle,
) {
  val palette = LocalKineticPalette.current
  val color = if (isCurrent) palette.accentText else palette.ink
  val text = move?.let { if (it.isGuess) "${it.san} ?" else it.san } ?: "—"
  Box(modifier = modifier) {
    Text(
      text = text,
      style = baseStyle.copy(color = color),
      fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
    )
  }
}
