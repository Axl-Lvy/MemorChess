package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import proj.memorchess.axl.ui.theme.AppThemeSetting
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Factor to apply to calculate the next training date. See
 * [INextDateCalculator][proj.memorchess.axl.core.date.INextDateCalculator]
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

val KEEP_LOGGED_IN_SETTING = ValueBasedAppConfigItem<Boolean, Boolean>("keepLoggedIn", true)

val ALL_SETTINGS_ITEMS =
  listOf(ON_SUCCESS_DATE_FACTOR_SETTING, TRAINING_MOVE_DELAY_SETTING, APP_THEME_SETTING)

expect val SETTINGS: Settings
