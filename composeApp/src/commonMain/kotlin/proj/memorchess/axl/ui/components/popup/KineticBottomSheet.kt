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
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.dialog_close
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/** Theme independent, so the scrim always reads as a dim. */
private const val SCRIM_ALPHA: Float = 0.5f

private val SCRIM_COLOR = Color.Black.copy(alpha = SCRIM_ALPHA)

/** Anchors popup content at the window origin, so the scrim can reach edge to edge. */
private object WindowOriginPopupPositionProvider : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset = IntOffset.Zero
}

/**
 * Bottom sheet with its own animated scrim. Built on [Popup] rather than a Dialog because
 * `DialogProperties.scrimColor` is skiko only, so a common Dialog would double the scrim.
 *
 * @param buttons trailing action buttons, aligned to the end beneath [content].
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
                onClickLabel = stringResource(Res.string.dialog_close),
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
              // Absorb taps so they don't fall through to the scrim behind the panel.
              .pointerInput(Unit) {}
              // A plain border, not kineticShadow: its offset shadow would fall outside the popup
              // on an edge-pinned sheet and get clipped.
              .border(width = 1.dp, color = palette.line)
              .background(palette.panel)
          ) {
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
