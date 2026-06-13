package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.ui.theme.AppThemeSetting
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme

/** Delay after each move in training mode before loading the next move */
val TRAINING_MOVE_DELAY_SETTING = DurationBasedConfigItem("trainingMoveDelay", 1.seconds)

val MOVE_ANIMATION_DURATION_SETTING =
  DurationBasedConfigItem("moveAnimationDuration", 500.milliseconds)

val APP_THEME_SETTING = EnumBasedAppConfigItem.from("appTheme", AppThemeSetting.SYSTEM)

val CHESS_BOARD_COLOR_SETTING =
  EnumBasedAppConfigItem.from("chessBoardColor", ChessBoardColorScheme.KINETIC_DARK)

/** Whether the evaluation bar is shown next to the board. */
val EVAL_BAR_ENABLED_SETTING = BooleanBasedConfigItem("evalBarEnabled", false)

/** Whether the best-move arrow overlay is drawn on the board. */
val BEST_MOVE_ARROW_ENABLED_SETTING = BooleanBasedConfigItem("bestMoveArrowEnabled", false)

/**
 * Maximum search depth for the Stockfish engine.
 *
 * Values 5–25 are literal depths; 0 means infinite (pass `null` to the engine).
 */
val ENGINE_MAX_DEPTH_SETTING = IntBasedConfigItem("engineMaxDepth", 20)

/**
 * Whether the FSRS scheduler applies interval fuzz, the small random spread that stops cards
 * reviewed together from bunching on the same future day. Off by default, matching canonical
 * FSRS 6.
 */
val FUZZ_ENABLED_SETTING = BooleanBasedConfigItem("schedulingFuzzEnabled", false)

/**
 * Whether the FSRS scheduler runs its short-term (learning steps) path. When on, a freshly failed
 * or brand-new position keeps reappearing within the session on sub-day steps until it graduates to
 * a day grained interval. On by default, matching canonical FSRS 6.
 */
val SHORT_TERM_ENABLED_SETTING = BooleanBasedConfigItem("schedulingShortTermEnabled", true)

/**
 * Maximum number of new positions introduced per day by the trainer. Zero means no new position is
 * introduced at all.
 */
val MAX_NEW_MOVES_PER_DAY_SETTING = IntBasedConfigItem("maxNewMovesPerDay", 10)

/**
 * Maximum number of distinct positions trained per day, reviews and new positions combined. Zero
 * means nothing is served, except cards mid learning steps which always finish their session.
 */
val MAX_TOTAL_MOVES_PER_DAY_SETTING = IntBasedConfigItem("maxTotalMovesPerDay", 100)

val ALL_SETTINGS_ITEMS =
  listOf(
    TRAINING_MOVE_DELAY_SETTING,
    MOVE_ANIMATION_DURATION_SETTING,
    APP_THEME_SETTING,
    CHESS_BOARD_COLOR_SETTING,
    ENGINE_MAX_DEPTH_SETTING,
    FUZZ_ENABLED_SETTING,
    SHORT_TERM_ENABLED_SETTING,
    MAX_NEW_MOVES_PER_DAY_SETTING,
    MAX_TOTAL_MOVES_PER_DAY_SETTING,
  )

internal expect fun getPlatformSpecificSettings(): Settings
