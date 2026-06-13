package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
 * Cached repertoire catalog manifest, stored as its raw JSON string. Internal state written by the
 * repertoire catalog cache, never shown in the settings UI. Empty means no cached manifest.
 */
val REPERTOIRE_MANIFEST_CACHE_SETTING = StringBasedConfig("repertoireManifestCache", "")

/** Moment the cached repertoire catalog manifest was fetched, used for staleness checks. */
val REPERTOIRE_MANIFEST_FETCHED_AT_SETTING =
  TimeBasedConfig("repertoireManifestFetchedAt", Instant.DISTANT_PAST)

/** Comma joined ids of the catalog repertoires installed on this device. */
val INSTALLED_REPERTOIRES_SETTING = StringBasedConfig("installedRepertoireIds", "")

val ALL_SETTINGS_ITEMS =
  listOf(
    TRAINING_MOVE_DELAY_SETTING,
    MOVE_ANIMATION_DURATION_SETTING,
    APP_THEME_SETTING,
    CHESS_BOARD_COLOR_SETTING,
    ENGINE_MAX_DEPTH_SETTING,
    FUZZ_ENABLED_SETTING,
    SHORT_TERM_ENABLED_SETTING,
    REPERTOIRE_MANIFEST_CACHE_SETTING,
    REPERTOIRE_MANIFEST_FETCHED_AT_SETTING,
    INSTALLED_REPERTOIRES_SETTING,
  )

internal expect fun getPlatformSpecificSettings(): Settings
