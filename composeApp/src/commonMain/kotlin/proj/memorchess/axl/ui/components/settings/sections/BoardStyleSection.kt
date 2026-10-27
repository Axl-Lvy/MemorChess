package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.ui.components.controls.KineticSwatch
import proj.memorchess.axl.ui.components.controls.KineticSwatchPicker
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme

/**
 * Swatch picker over [ChessBoardColorScheme.entries], persisted via [CHESS_BOARD_COLOR_SETTING].
 *
 * @param reloadKey Changing it re-reads the persisted setting.
 */
@Composable
fun BoardStyleSection(reloadKey: Any, onReload: () -> Unit = {}) {
  var current by
    remember(reloadKey) {
      mutableStateOf<ChessBoardColorScheme>(CHESS_BOARD_COLOR_SETTING.getValue())
    }
  val options =
    ChessBoardColorScheme.entries.map {
      KineticSwatch(
        value = it,
        label = it.displayName,
        lightSquareColor = it.lightSquareColor,
        darkSquareColor = it.darkSquareColor,
      )
    }
  KineticSwatchPicker(
    options = options,
    selected = current,
    onSelect = {
      current = it
      CHESS_BOARD_COLOR_SETTING.setValue(it)
      onReload()
    },
    modifier = Modifier.fillMaxWidth().testTag(CHESS_BOARD_COLOR_SETTING.name),
  )
}
