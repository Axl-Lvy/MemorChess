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

/** Room on the shadow side so the big [kineticShadow] is not clipped by the dialog window. */
private val SHADOW_ROOM = 14.dp

/**
 * Kinetic dialog panel that animates in and out with [KineticMotion.hudEnter] / [hudExit]. Once the
 * exit settles, the dialog leaves the tree entirely.
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

  // A raw Dialog vanishes at once, so keep the window until the exit animation settles.
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
        // Inside AnimatedVisibility so the shadow room scales and fades with the panel.
        Box(modifier = Modifier.padding(end = SHADOW_ROOM, bottom = SHADOW_ROOM)) {
          Column(
            // kineticShadow precedes background so the shadow is drawn behind the panel fill.
            modifier = modifier.fillMaxWidth().kineticShadow(big = true).background(palette.panel)
          ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(palette.actionGlow))
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
