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
 * Kinetic motion tokens.
 *
 * Most of the Kinetic design system's motion follows "Register, don't drift": hard edges and flat
 * offset shadows carry through to a fast attack, a hard settle, and no overshoot. [Celebratory]
 * reverses that law on purpose. Gamification payoffs (a correct answer, a streak milestone) are
 * meant to feel like a reward, and a reward that registers instead of bouncing reads as flat, not
 * disciplined, so those two moments get real springs with visible overshoot. The interactions that
 * gamification does not touch use [Routine] instead, the fast family with no overshoot the rest of
 * the app already expects. The legacy tween and transition helpers below (registerTween,
 * sweepTween, holdEnter, holdExit, hudEnter, hudExit) sit alongside [Routine] rather than inside
 * it.
 *
 * The [Duration] and [Easing] tokens just below (instant, register, travel, attack, pieceGlide,
 * sweep) are object level vals, allocated once. Every animation spec, in [Celebratory] and
 * [Routine] alike, is instead built by a function that allocates a fresh spec on each call. The
 * ones whose spec depends on reduce motion also read [REDUCE_MOTION_SETTING] from the persisted
 * Settings store on that same call, so building one costs an allocation plus a settings read: build
 * it once where the animation is launched, not on every recomposition. Consumers drive the built
 * spec through the draw or layout phase (`graphicsLayer`, `offset { }`, `drawBehind`) reading an
 * `Animatable`/transition value inside the lambda, never by reading an animated value while
 * composing. That keeps the single threaded wasmJs target lag free.
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

  /**
   * Incoming transition for a navigation between two bottom nav tabs: a slide from [fromRight]'s
   * edge plus a fade, timed by [Routine.screenTransition]. Collapses to a plain fade when reduce
   * motion is on.
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

  /** Whether the bottom nav icon pop should play. False when reduce motion is on. */
  fun shouldPlayIconPop(): Boolean = !reduceMotionEnabled()

  /**
   * Whether the board training feedback overlays (correct/wrong square animations, the "+1"
   * floater, the counter roll) should animate. False when reduce motion is on: every one of those
   * effects is skipped outright, with its end state rendered immediately instead.
   */
  fun shouldAnimateBoardFeedback(): Boolean = !reduceMotionEnabled()

  /**
   * Springs with visible overshoot, reserved for the two gamification payoffs the "register, don't
   * drift" law deliberately excludes: a correct answer and a streak milestone.
   */
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

  /**
   * Fast springs and short tweens with no overshoot, for the interactions gamification does not
   * touch. This is the "register, don't drift" law given concrete spec values.
   */
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

    /**
     * Bottom nav icon pop on selection: a fast, settled bump to 1.12x and back. Reuses
     * [screenTransition]'s spring since the mockup pins only the pop's amplitude, not its own
     * spring curve.
     */
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
