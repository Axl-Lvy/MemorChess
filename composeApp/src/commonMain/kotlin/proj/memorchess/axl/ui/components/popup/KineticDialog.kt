package proj.memorchess.axl.ui.components.popup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.kineticShadow

/**
 * Space reserved on the panel's shadow side so the hard offset shadow ([kineticShadow] with `big =
 * true`, a 12.dp offset plus a 1.dp border) renders fully inside the dialog window instead of being
 * clipped by its tight content-wrapping bounds.
 */
private val SHADOW_ROOM = 14.dp

/**
 * Kinetic "power-on" dialog: a hard-edged panel with a flat offset shadow and a bright accent strip
 * along its top edge, that registers in and out with [KineticMotion.hudEnter] / [hudExit] instead
 * of snapping. The platform scrim appears immediately under it.
 *
 * The underlying [Dialog] is kept mounted for the whole enter **and** exit transition by driving an
 * [AnimatedVisibility] off a [MutableTransitionState]; the window is only torn down once the exit
 * has fully settled. That is what lets the panel animate *out* (a raw `Dialog` otherwise vanishes
 * the instant its flag flips) while still being completely absent from the tree afterwards — which
 * the settings dialog tests rely on.
 *
 * @param visible Whether the dialog should be shown.
 * @param onDismissRequest Invoked when the user dismisses via scrim tap or system back.
 * @param modifier Modifier applied to the dialog panel (e.g. a `testTag`).
 * @param buttons Trailing action buttons, laid out end-aligned beneath [content].
 * @param content The dialog body (typically a title/message).
 */
@Composable
fun KineticDialog(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  buttons: @Composable RowScope.() -> Unit = {},
  content: @Composable () -> Unit,
) {
  val transitionState = remember { MutableTransitionState(false) }
  transitionState.targetState = visible

  // Keep the window alive while visible OR while the exit animation is still running.
  if (transitionState.currentState || transitionState.targetState) {
    val palette = LocalKineticPalette.current
    Dialog(
      onDismissRequest = onDismissRequest,
      properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
      AnimatedVisibility(
        visibleState = transitionState,
        enter = KineticMotion.hudEnter(),
        exit = KineticMotion.hudExit(),
      ) {
        // Reserve room on the shadow side so the hard offset block renders in full. The Dialog
        // window wraps its content tightly; without this padding the bottom/right shadow strip
        // falls outside the window and is clipped — which both looked broken at rest and made the
        // shadow appear to "snap in" only once the enter scale settled. With the padding inside the
        // AnimatedVisibility subtree the shadow now scales and fades together with the panel.
        Box(modifier = Modifier.padding(end = SHADOW_ROOM, bottom = SHADOW_ROOM)) {
          Column(
            // kineticShadow must precede background so the hard offset block is drawn *behind* the
            // panel fill (the panel then paints over the overlap, leaving only the offset strip).
            // With background first the shadow painted on top of the panel.
            modifier = modifier.fillMaxWidth().kineticShadow(big = true).background(palette.panel)
          ) {
            // Bright HUD strip flush to the top edge.
            Box(Modifier.fillMaxWidth().height(2.dp).background(palette.accentGlow))
            Column(modifier = Modifier.padding(20.dp)) {
              content()
              Spacer(modifier = Modifier.height(16.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                content = buttons,
              )
            }
          }
        }
      }
    }
  }
}
