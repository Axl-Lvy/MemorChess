package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Minimum time to wait before loading page */
val MINIMUM_LOADING_TIME_SETTING =
  AppConfigItem(
    "minimumLoadingTime",
    0.5.seconds,
    { long -> long.milliseconds },
    { duration -> duration.inWholeMilliseconds },
  )

/**
 * Factor to apply to calculate the next training date. See
 * [INextDateCalculator][proj.memorchess.axl.core.date.INextDateCalculator]
 */
val ON_SUCCESS_DATE_FACTOR_SETTING = AppConfigItem<Double, Double>("onSuccessDateFactor", 1.5)

val ALL_SETTINGS_ITEMS = listOf(MINIMUM_LOADING_TIME_SETTING, ON_SUCCESS_DATE_FACTOR_SETTING)

expect val settings: Settings
