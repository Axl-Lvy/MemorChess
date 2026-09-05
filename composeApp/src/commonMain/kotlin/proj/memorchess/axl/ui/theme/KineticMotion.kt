package proj.memorchess.axl.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING

/**
 * Kinetic motion tokens.
 *
 * Most of the Kinetic design system's motion follows "Register, don't drift": hard edges and flat
 * offset shadows carry through to a fast attack, a hard settle, and no overshoot. [Celebratory]
 * reverses that law on purpose. Gamification payoffs (a correct answer, a streak milestone) are
 * meant to feel like a reward, and a reward that registers instead of bouncing reads as flat, not
 * disciplined, so those two moments get real springs with visible overshoot. Everything else stays
 * on [Routine], the fast, no-overshoot family the rest of the app already expects.
 *
 * Every value is a hoisted top-level constant so animation specs are allocated once rather than on
 * every recomposition. Consumers must drive these through the draw/layout phase (`graphicsLayer`,
 * `offset { }`, `drawBehind`) reading an `Animatable`/transition value inside the lambda. Never
 * read an animated value in the composition phase. That keeps animations recomposition-free and
 * lag-free, including on the single-threaded wasmJs target.
 */
object KineticMotion {

  /** Feedback flashes and the smallest state flips (toggle, training correct/incorrect tick). */
  val instant: Duration = 90.milliseconds

  /** In-screen swaps: dialogs, loading reveal, settings section changes. */
  val register: Duration = 180.milliseconds

  /** Screen-to-screen accent sweep — long enough to read as a deliberate scan across the screen. */
  val travel: Duration = 420.milliseconds

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

  private fun Duration.ms(): Int = inWholeMilliseconds.toInt()

  /** A [tween] over [register] using the signature [attack] easing. */
  fun <T> registerTween(): FiniteAnimationSpec<T> = tween(register.ms(), easing = attack)

  /** A constant-velocity [tween] over [travel], for the screen-transition accent [sweep]. */
  fun <T> sweepTween(): FiniteAnimationSpec<T> = tween(travel.ms(), easing = sweep)

  /**
   * No-op enter that keeps an incoming `NavHost` destination composed for the full [travel] window
   * without any visible fade (alpha stays at 1). It exists only to give the two-panel curtain wipe
   * a running transition clock to drive the clip from — the visual is done by the wipe-reveal clip,
   * not by this fade.
   */
  fun holdEnter(): EnterTransition = fadeIn(animationSpec = sweepTween(), initialAlpha = 1f)

  /**
   * No-op exit mirroring [holdEnter]: keeps the outgoing destination fully visible for [travel].
   */
  fun holdExit(): ExitTransition = fadeOut(animationSpec = sweepTween(), targetAlpha = 1f)

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

  /** Duration every [Celebratory]/[Routine] spring collapses to when reduce motion is on. */
  private val reducedMotionDuration: Duration = 120.milliseconds

  /** Whether [REDUCE_MOTION_SETTING] is on. */
  private fun reduceMotionEnabled(): Boolean = REDUCE_MOTION_SETTING.getValue()

  /**
   * A [spring] with the given [dampingRatio] and [stiffness], collapsed to a flat
   * [reducedMotionDuration] tween when reduce motion is on.
   */
  private fun <T> reducibleSpring(dampingRatio: Float, stiffness: Float): FiniteAnimationSpec<T> =
    if (reduceMotionEnabled()) tween(reducedMotionDuration.ms(), easing = attack)
    else spring(dampingRatio = dampingRatio, stiffness = stiffness)

  /**
   * Whether the streak-milestone overlay should appear. False when reduce motion is on, per the
   * mockup's accessibility note: the overlay is skipped entirely rather than shown flat.
   */
  fun shouldShowStreakMilestone(): Boolean = !reduceMotionEnabled()

  /**
   * Springs with visible overshoot, reserved for the two gamification payoffs the "register, don't
   * drift" law deliberately excludes: a correct answer and a streak milestone.
   */
  object Celebratory {
    /** Correct-answer feedback: a quick, bouncy pop. */
    fun <T> correctAnswer(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = 0.4f, stiffness = 500f)

    /** Streak-milestone overlay entrance: the deepest, slowest bounce, for the rarest payoff. */
    fun <T> streakMilestone(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = 0.35f, stiffness = 400f)
  }

  /**
   * Fast springs and short tweens with no overshoot, for the interactions gamification does not
   * touch. This is the "register, don't drift" law given concrete spec values.
   */
  object Routine {
    /** Wrong-answer feedback: a flat, immediate register with no bounce. */
    fun <T> wrongAnswer(): FiniteAnimationSpec<T> = tween(120, easing = attack)

    /** Tab and screen transitions: a fast, settled slide. */
    fun <T> screenTransition(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = 0.8f, stiffness = 600f)

    /** Button press: the tightest, fastest spring, for an immediate tactile response. */
    fun <T> buttonPress(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = 0.85f, stiffness = 700f)

    /** Bottom sheet slide: a fast settle with slightly more visible mass than [buttonPress]. */
    fun <T> bottomSheet(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = 0.78f, stiffness = 560f)

    /** Loading skeleton shimmer: the longest routine tween, paced for a readable pulse. */
    fun <T> loadingSkeleton(): FiniteAnimationSpec<T> = tween(300, easing = LinearEasing)
  }
}
