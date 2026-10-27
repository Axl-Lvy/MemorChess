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
  EnumBasedAppConfigItem.from("chessBoardColor", ChessBoardColorScheme.KINETIC_VIOLET)

/** Whether the evaluation bar is shown next to the board. */
val EVAL_BAR_ENABLED_SETTING = BooleanBasedConfigItem("evalBarEnabled", false)

/** Whether the best-move arrow overlay is drawn on the board. */
val BEST_MOVE_ARROW_ENABLED_SETTING = BooleanBasedConfigItem("bestMoveArrowEnabled", false)

/**
 * Whether motion is reduced. When on, [proj.memorchess.axl.ui.theme.KineticMotion]'s spring tokens
 * resolve to a flat, no-bounce tween and the streak-milestone overlay is skipped entirely.
 */
val REDUCE_MOTION_SETTING = BooleanBasedConfigItem("reduceMotionEnabled", false)

/** Maximum Stockfish search depth, from 5 to 25, or 0 for infinite. */
val ENGINE_MAX_DEPTH_SETTING = IntBasedConfigItem("engineMaxDepth", 20)

/**
 * Whether the FSRS scheduler applies interval fuzz, so cards reviewed together do not bunch on the
 * same future day. Off by default, matching canonical FSRS 6.
 */
val FUZZ_ENABLED_SETTING = BooleanBasedConfigItem("schedulingFuzzEnabled", false)

/**
 * Whether the FSRS scheduler runs learning steps, re-showing new or failed positions within the
 * session until they graduate to a day grained interval. On by default, matching canonical FSRS 6.
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

/**
 * Cached repertoire catalog manifest as raw JSON, empty when none. Internal state, never shown in
 * the settings UI.
 */
val REPERTOIRE_MANIFEST_CACHE_SETTING = StringBasedConfig("repertoireManifestCache", "")

/** Moment the cached repertoire catalog manifest was fetched, used for staleness checks. */
val REPERTOIRE_MANIFEST_FETCHED_AT_SETTING =
  TimeBasedConfig("repertoireManifestFetchedAt", Instant.DISTANT_PAST)

/** Comma joined `localId=slug` pairs recording each repertoire's last published public slug. */
val PUBLISHED_REPERTOIRES_SETTING = StringBasedConfig("publishedRepertoireSlugs", "")

val ALL_SETTINGS_ITEMS =
  listOf(
    TRAINING_MOVE_DELAY_SETTING,
    MOVE_ANIMATION_DURATION_SETTING,
    APP_THEME_SETTING,
    CHESS_BOARD_COLOR_SETTING,
    REDUCE_MOTION_SETTING,
    ENGINE_MAX_DEPTH_SETTING,
    FUZZ_ENABLED_SETTING,
    SHORT_TERM_ENABLED_SETTING,
    MAX_NEW_MOVES_PER_DAY_SETTING,
    MAX_TOTAL_MOVES_PER_DAY_SETTING,
    REPERTOIRE_MANIFEST_CACHE_SETTING,
    REPERTOIRE_MANIFEST_FETCHED_AT_SETTING,
    PUBLISHED_REPERTOIRES_SETTING,
  )

internal expect fun getPlatformSpecificSettings(): Settings
