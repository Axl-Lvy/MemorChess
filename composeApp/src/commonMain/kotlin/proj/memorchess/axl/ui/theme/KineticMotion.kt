package proj.memorchess.axl.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Kinetic motion tokens — the "Register, don't drift" motion language.
 *
 * The Kinetic design system is built from hard edges, zero-radius shapes and flat (blur-less)
 * offset shadows; its motion mirrors that. Things do not gently ease into place, they *register*: a
 * fast attack, a hard settle, and **no overshoot** — there are deliberately no spring tokens here.
 *
 * Every value is a hoisted top-level constant so animation specs are allocated once rather than on
 * every recomposition. Consumers must drive these through the draw/layout phase (`graphicsLayer`,
 * `offset { }`, `drawBehind`) reading an `Animatable`/transition value inside the lambda — never by
 * reading an animated value in the composition phase — so animations stay recomposition-free and
 * lag-free, including on the single-threaded wasmJs target.
 */
object KineticMotion {

  /** Feedback flashes and the smallest state flips (toggle, training correct/incorrect tick). */
  val instant: Duration = 90.milliseconds

  /** In-screen swaps: dialogs, loading reveal, settings section changes. */
  val register: Duration = 180.milliseconds

  /** Screen-to-screen telemetry wipe. */
  val travel: Duration = 240.milliseconds

  /**
   * Signature easing: an instant attack that settles hard, with no overshoot. Used for everything
   * that should read as a precision instrument "registering" a value.
   */
  val attack: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

  /**
   * The one deliberate exception to [attack]. A sliding chess piece is physical mass, so a gentle
   * deceleration reads more naturally than the snappy [attack] used for UI chrome.
   */
  val pieceGlide: Easing = FastOutSlowInEasing

  /**
   * Constant-velocity easing for the accent sweep line — a mechanical wipe, not a decelerating one.
   */
  val sweep: Easing = LinearEasing

  /**
   * Fraction of the container a wiping screen/section travels. Kept small: the motion reads as a
   * panel registering into place behind a hard edge, not a full-screen slide.
   */
  const val WIPE_OFFSET_FRACTION: Float = 0.14f

  private fun Duration.ms(): Int = inWholeMilliseconds.toInt()

  /** A [tween] over [register] using the signature [attack] easing. */
  fun <T> registerTween(): FiniteAnimationSpec<T> = tween(register.ms(), easing = attack)

  /** A [tween] over [travel] using the signature [attack] easing. */
  fun <T> travelTween(): FiniteAnimationSpec<T> = tween(travel.ms(), easing = attack)

  /**
   * Enter half of an axis wipe: content registers in from the leading edge (right when [forward],
   * left otherwise) over [travel], fading up behind the advancing edge.
   *
   * @param forward Whether navigation is moving toward a higher-ordinal destination.
   */
  fun axisWipeEnter(forward: Boolean): EnterTransition {
    val sign = if (forward) 1 else -1
    return slideInHorizontally(animationSpec = travelTween()) { width ->
      (sign * width * WIPE_OFFSET_FRACTION).toInt()
    } + fadeIn(animationSpec = travelTween())
  }

  /**
   * Exit half of an axis wipe: the outgoing content slides a short distance opposite the incoming
   * one and fades to nothing over [travel].
   *
   * @param forward Whether navigation is moving toward a higher-ordinal destination.
   */
  fun axisWipeExit(forward: Boolean): ExitTransition {
    val sign = if (forward) 1 else -1
    return slideOutHorizontally(animationSpec = travelTween()) { width ->
      (-sign * width * WIPE_OFFSET_FRACTION).toInt()
    } + fadeOut(animationSpec = travelTween())
  }

  /** Scale a HUD element registers in from, just short of full size. */
  private const val HUD_INITIAL_SCALE: Float = 0.94f

  /**
   * "Power-on" enter for HUD surfaces (dialogs, the promotion selector): fades up while registering
   * in from [HUD_INITIAL_SCALE] over [register] with the signature [attack] easing — a fast, hard
   * settle with no overshoot.
   */
  fun hudEnter(): EnterTransition =
    fadeIn(animationSpec = registerTween()) +
      scaleIn(initialScale = HUD_INITIAL_SCALE, animationSpec = registerTween())

  /**
   * "Power-off" exit mirroring [hudEnter]: fades out while collapsing back to [HUD_INITIAL_SCALE].
   */
  fun hudExit(): ExitTransition =
    fadeOut(animationSpec = registerTween()) +
      scaleOut(targetScale = HUD_INITIAL_SCALE, animationSpec = registerTween())
}
