package proj.memorchess.axl.core.config

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Minimum time to wait before loading page */
val MINIMUM_LOADING_TIME_SETTING =
  ValueBasedAppConfigItem(
    "minimumLoadingTime",
    0.5.seconds,
    { long -> long.milliseconds },
    { duration -> duration.inWholeMilliseconds },
  )

val AUTH_REFRESH_TOKEN_SETTINGS = ValueBasedAppConfigItem<String, String>("authRefreshToken", "")

val AUTH_ACCESS_TOKEN_SETTINGS = ValueBasedAppConfigItem<String, String>("authAccessToken", "")

val LAST_SYNC =
  ValueBasedAppConfigItem(
    "lastSync",
    Instant.DISTANT_PAST,
    { Instant.parse(it) },
    { it.toString() },
  )
