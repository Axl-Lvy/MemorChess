package proj.memorchess.axl.ui.components.popup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.kineticShadow

/**
 * Scrim darkness, a fixed value independent of theme so it always reads as a dim, not a highlight.
 */
private const val SCRIM_ALPHA: Float = 0.5f

private val SCRIM_COLOR = Color.Black.copy(alpha = SCRIM_ALPHA)

/**
 * Always anchors popup content at the window origin, so a full-bleed scrim reaches every edge
 * regardless of where [KineticBottomSheet] sits in the caller's own layout tree.
 */
private object WindowOriginPopupPositionProvider : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset = IntOffset.Zero
}

/**
 * Bottom sheet with its own animated scrim, registering in and out with
 * [KineticMotion.Routine.bottomSheet] instead of snapping. Built on [Popup] rather than
 * [Dialog][androidx.compose.ui.window.Dialog]: `DialogProperties.scrimColor` only exists on the
 * skiko target, so a `commonMain` dialog cannot suppress the platform's own instant scrim, which
 * would otherwise double up with this one.
 *
 * The mount guard keeps the [Popup] alive for the whole exit animation (mirroring [KineticDialog]'s
 * own [AnimatedVisibility] placement inside its host window), so the scrim and sheet both finish
 * sliding/fading before the popup actually tears down.
 *
 * @param visible Whether the sheet should be shown.
 * @param onDismissRequest Invoked when the user dismisses via scrim tap or system back.
 * @param modifier Modifier applied to the sheet panel (e.g. a `testTag`).
 * @param buttons Trailing action buttons, laid out end-aligned beneath [content].
 * @param content The sheet body.
 */
@Composable
internal fun KineticBottomSheet(
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
    Popup(
      onDismissRequest = onDismissRequest,
      popupPositionProvider = WindowOriginPopupPositionProvider,
      properties =
        PopupProperties(
          focusable = true,
          dismissOnBackPress = true,
          // The sheet's own scrim click handles outside dismissal so it can drive the exit
          // animation instead of the popup tearing down instantly.
          dismissOnClickOutside = false,
          // The scrim must reach behind system bars and a notch, not stop at the safe area.
          usePlatformInsets = false,
        ),
    ) {
      AnimatedVisibility(
        visibleState = transitionState,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
      ) {
        Box(Modifier.fillMaxSize()) {
          Box(
            Modifier.fillMaxSize()
              .animateEnterExit(
                enter = fadeIn(animationSpec = KineticMotion.registerTween()),
                exit = fadeOut(animationSpec = KineticMotion.registerTween()),
              )
              .background(SCRIM_COLOR)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) {
                onDismissRequest()
              }
          )
          Column(
            modifier
              .align(Alignment.BottomCenter)
              .animateEnterExit(
                enter =
                  slideInVertically(animationSpec = KineticMotion.Routine.bottomSheet()) { it },
                exit =
                  slideOutVertically(animationSpec = KineticMotion.Routine.bottomSheet()) { it },
              )
              .fillMaxWidth()
              .kineticShadow(big = true)
              .background(palette.panel)
          ) {
            // HUD strip flush to the top edge, mirrors KineticDialog.
            Box(Modifier.fillMaxWidth().height(2.dp).background(palette.actionGlow))
            Column(Modifier.padding(20.dp)) {
              content()
              Spacer(Modifier.height(16.dp))
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
