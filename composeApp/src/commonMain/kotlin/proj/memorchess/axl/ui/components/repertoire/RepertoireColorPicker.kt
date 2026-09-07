package proj.memorchess.axl.ui.components.repertoire

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.library_color_black
import memorchess.composeapp.generated.resources.library_color_white
import memorchess.composeapp.generated.resources.library_create_color_mixed
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.ui.components.controls.KineticSegmentedControl

/**
 * White/Black/Mixed (`null`) side picker shared by [CreateRepertoireDialog] and
 * [ForkRepertoireDialog].
 */
@Composable
internal fun RepertoireColorPicker(
  selected: RepertoireColor?,
  onSelect: (RepertoireColor?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val whiteLabel = stringResource(Res.string.library_color_white)
  val blackLabel = stringResource(Res.string.library_color_black)
  val mixedLabel = stringResource(Res.string.library_create_color_mixed)
  KineticSegmentedControl(
    options = listOf(RepertoireColor.WHITE, RepertoireColor.BLACK, null),
    selected = selected,
    onSelect = onSelect,
    label = {
      when (it) {
        RepertoireColor.WHITE -> whiteLabel
        RepertoireColor.BLACK -> blackLabel
        null -> mixedLabel
      }
    },
    modifier = modifier,
  )
}
