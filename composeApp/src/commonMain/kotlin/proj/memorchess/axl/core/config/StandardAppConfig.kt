package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.ui.theme.AppThemeSetting
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme

/**
 * Factor to apply to calculate the next training date. See
 * [NextDateCalculator][proj.memorchess.axl.core.date.NextDateCalculator]
 */
val ON_SUCCESS_DATE_FACTOR_SETTING = DoubleBasedConfigItem("onSuccessDateFactor", 1.5)

/** Delay after each move in training mode before loading the next move */
val TRAINING_MOVE_DELAY_SETTING = DurationBasedConfigItem("trainingMoveDelay", 1.seconds)

val MOVE_ANIMATION_DURATION_SETTING =
  DurationBasedConfigItem("moveAnimationDuration", 500.milliseconds)

val APP_THEME_SETTING = EnumBasedAppConfigItem.from("appTheme", AppThemeSetting.SYSTEM)

val CHESS_BOARD_COLOR_SETTING =
  EnumBasedAppConfigItem.from("chessBoardColor", ChessBoardColorScheme.WOOD)

val KEEP_LOGGED_IN_SETTING = BooleanBasedConfigItem("keepLoggedIn", false)

/** Whether the evaluation bar is shown next to the board. */
val EVAL_BAR_ENABLED_SETTING = BooleanBasedConfigItem("evalBarEnabled", false)

/**
 * Maximum search depth for the Stockfish engine.
 *
 * Values 5–25 are literal depths; 0 means infinite (pass `null` to the engine).
 */
val ENGINE_MAX_DEPTH_SETTING = IntBasedConfigItem("engineMaxDepth", 20)

val ALL_SETTINGS_ITEMS =
  listOf(
    ON_SUCCESS_DATE_FACTOR_SETTING,
    TRAINING_MOVE_DELAY_SETTING,
    MOVE_ANIMATION_DURATION_SETTING,
    APP_THEME_SETTING,
    CHESS_BOARD_COLOR_SETTING,
    ENGINE_MAX_DEPTH_SETTING,
  )

internal expect fun getPlatformSpecificSettings(): Settings
