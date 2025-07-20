package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.ui.theme.AppThemeSetting

/**
 * Factor to apply to calculate the next training date. See
 * [NextDateCalculator][proj.memorchess.axl.core.date.NextDateCalculator]
 */
val ON_SUCCESS_DATE_FACTOR_SETTING =
  ValueBasedAppConfigItem<Double, Double>("onSuccessDateFactor", 1.5)

/** Delay after each move in training mode before loading the next move */
val TRAINING_MOVE_DELAY_SETTING =
  ValueBasedAppConfigItem(
    "trainingMoveDelay",
    1.seconds,
    { long -> long.milliseconds },
    { duration -> duration.inWholeMilliseconds },
  )

val APP_THEME_SETTING = EnumBasedAppConfigItem.from("appTheme", AppThemeSetting.SYSTEM)

val KEEP_LOGGED_IN_SETTING = ValueBasedAppConfigItem<Boolean, Boolean>("keepLoggedIn", false)

val ALL_SETTINGS_ITEMS =
  listOf(ON_SUCCESS_DATE_FACTOR_SETTING, TRAINING_MOVE_DELAY_SETTING, APP_THEME_SETTING)

expect val SETTINGS: Settings
