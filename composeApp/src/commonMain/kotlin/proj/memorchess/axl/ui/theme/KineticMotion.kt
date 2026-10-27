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
import androidx.compose.animation.slideInHorizontally
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING

/**
 * Kinetic motion tokens: overshoot-free [Routine] specs and bouncy [Celebratory] ones. Builders
 * allocate and may read [REDUCE_MOTION_SETTING] on each call, so build once per animation, and read
 * animated values in the draw or layout phase, never in composition, to keep wasmJs lag free.
 */
object KineticMotion {

  /** Feedback flashes and the smallest state flips (toggle, training correct/incorrect tick). */
  val instant: Duration = 90.milliseconds

  /** In-screen swaps: dialogs, loading reveal, settings section changes. */
  val register: Duration = 180.milliseconds

  /** Screen-to-screen accent sweep. */
  val travel: Duration = 420.milliseconds

  /** Signature easing: an instant attack that settles hard, with no overshoot. */
  val attack: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

  /** Gentle deceleration for sliding chess pieces, the one exception to [attack]. */
  val pieceGlide: Easing = FastOutSlowInEasing

  /** Constant-velocity easing for the accent sweep line. */
  val sweep: Easing = LinearEasing

  private fun Duration.ms(): Int = inWholeMilliseconds.toInt()

  /** A [tween] over [register] using the signature [attack] easing. */
  fun <T> registerTween(): FiniteAnimationSpec<T> = tween(register.ms(), easing = attack)

  /** A constant-velocity [tween] over [travel], for the screen-transition accent [sweep]. */
  fun <T> sweepTween(): FiniteAnimationSpec<T> = tween(travel.ms(), easing = sweep)

  /**
   * No-op enter keeping an incoming `NavHost` destination composed for [travel], so the curtain
   * wipe has a transition clock to drive its clip.
   */
  fun holdEnter(): EnterTransition = fadeIn(animationSpec = sweepTween(), initialAlpha = 1f)

  /** No-op exit mirroring [holdEnter]: keeps the outgoing destination visible for [travel]. */
  fun holdExit(): ExitTransition = fadeOut(animationSpec = sweepTween(), targetAlpha = 1f)

  /**
   * Incoming bottom nav tab transition: a slide from [fromRight]'s edge plus a fade, or only a fade
   * when reduce motion is on.
   */
  fun tabEnter(fromRight: Boolean): EnterTransition =
    if (reduceMotionEnabled()) fadeIn(animationSpec = Routine.screenTransition())
    else
      slideInHorizontally(animationSpec = Routine.screenTransition()) { fullWidth ->
        if (fromRight) fullWidth else -fullWidth
      } + fadeIn(animationSpec = Routine.screenTransition())

  /** Outgoing transition for a navigation between two bottom nav tabs: a fade alone. */
  fun tabExit(): ExitTransition = fadeOut(animationSpec = Routine.screenTransition())

  /** Scale a HUD element registers in from, just short of full size. */
  private const val HUD_INITIAL_SCALE: Float = 0.94f

  /** "Power-on" enter for HUD surfaces, fading up while scaling in from [HUD_INITIAL_SCALE]. */
  fun hudEnter(): EnterTransition =
    fadeIn(animationSpec = registerTween()) +
      scaleIn(initialScale = HUD_INITIAL_SCALE, animationSpec = registerTween())

  /** "Power-off" exit mirroring [hudEnter]. */
  fun hudExit(): ExitTransition =
    fadeOut(animationSpec = registerTween()) +
      scaleOut(targetScale = HUD_INITIAL_SCALE, animationSpec = registerTween())

  /** Duration every [Celebratory]/[Routine] spring collapses to when reduce motion is on. */
  private val reducedMotionDuration: Duration = 120.milliseconds

  private fun reduceMotionEnabled(): Boolean = REDUCE_MOTION_SETTING.getValue()

  /** A [spring], collapsed to a flat [reducedMotionDuration] tween when reduce motion is on. */
  private fun <T> reducibleSpring(dampingRatio: Float, stiffness: Float): FiniteAnimationSpec<T> =
    if (reduceMotionEnabled()) tween(reducedMotionDuration.ms(), easing = attack)
    else spring(dampingRatio = dampingRatio, stiffness = stiffness)

  /** Whether the streak-milestone overlay should appear. False when reduce motion is on. */
  fun shouldShowStreakMilestone(): Boolean = !reduceMotionEnabled()

  /** Whether the bottom nav icon pop should play. False when reduce motion is on. */
  fun shouldPlayIconPop(): Boolean = !reduceMotionEnabled()

  /**
   * Whether the board training feedback overlays should animate. False when reduce motion is on, in
   * which case their end state renders immediately.
   */
  fun shouldAnimateBoardFeedback(): Boolean = !reduceMotionEnabled()

  /** Springs with visible overshoot, reserved for a correct answer and a streak milestone. */
  object Celebratory {
    private const val CORRECT_ANSWER_DAMPING: Float = 0.4f
    private const val CORRECT_ANSWER_STIFFNESS: Float = 500f
    private const val STREAK_MILESTONE_DAMPING: Float = 0.35f
    private const val STREAK_MILESTONE_STIFFNESS: Float = 400f

    /** Correct-answer feedback: a quick, bouncy pop. */
    fun <T> correctAnswer(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = CORRECT_ANSWER_DAMPING, stiffness = CORRECT_ANSWER_STIFFNESS)

    /** Streak-milestone overlay entrance: the deepest, slowest bounce, for the rarest payoff. */
    fun <T> streakMilestone(): FiniteAnimationSpec<T> =
      reducibleSpring(
        dampingRatio = STREAK_MILESTONE_DAMPING,
        stiffness = STREAK_MILESTONE_STIFFNESS,
      )
  }

  /** Fast springs and short tweens with no overshoot, for interactions outside gamification. */
  object Routine {
    private const val WRONG_ANSWER_DURATION_MS: Int = 120
    private const val SCREEN_TRANSITION_DAMPING: Float = 0.8f
    private const val SCREEN_TRANSITION_STIFFNESS: Float = 600f
    private const val BUTTON_PRESS_DAMPING: Float = 0.85f
    private const val BUTTON_PRESS_STIFFNESS: Float = 700f
    private const val BOTTOM_SHEET_DAMPING: Float = 0.78f
    private const val BOTTOM_SHEET_STIFFNESS: Float = 560f
    private const val LOADING_SKELETON_DURATION_MS: Int = 300

    /** Wrong-answer feedback: a flat, immediate register with no bounce. */
    fun <T> wrongAnswer(): FiniteAnimationSpec<T> = tween(WRONG_ANSWER_DURATION_MS, easing = attack)

    /** Tab and screen transitions: a fast, settled slide. */
    fun <T> screenTransition(): FiniteAnimationSpec<T> =
      reducibleSpring(
        dampingRatio = SCREEN_TRANSITION_DAMPING,
        stiffness = SCREEN_TRANSITION_STIFFNESS,
      )

    /** Button press: the tightest, fastest spring, for an immediate tactile response. */
    fun <T> buttonPress(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = BUTTON_PRESS_DAMPING, stiffness = BUTTON_PRESS_STIFFNESS)

    /** Bottom sheet slide: a fast settle with slightly more visible mass than [buttonPress]. */
    fun <T> bottomSheet(): FiniteAnimationSpec<T> =
      reducibleSpring(dampingRatio = BOTTOM_SHEET_DAMPING, stiffness = BOTTOM_SHEET_STIFFNESS)

    /** Bottom nav icon pop on selection, sharing [screenTransition]'s spring. */
    fun <T> iconPop(): FiniteAnimationSpec<T> =
      reducibleSpring(
        dampingRatio = SCREEN_TRANSITION_DAMPING,
        stiffness = SCREEN_TRANSITION_STIFFNESS,
      )

    /** Loading skeleton shimmer: the longest routine tween, paced for a readable pulse. */
    fun <T> loadingSkeleton(): FiniteAnimationSpec<T> =
      tween(LOADING_SKELETON_DURATION_MS, easing = LinearEasing)
  }
}
