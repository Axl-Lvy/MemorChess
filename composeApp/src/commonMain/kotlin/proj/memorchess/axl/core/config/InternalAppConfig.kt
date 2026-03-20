package proj.memorchess.axl.core.config

import kotlin.time.Duration.Companion.seconds

/** Minimum time to wait before loading page */
val MINIMUM_LOADING_TIME_SETTING = DurationBasedConfigItem("minimumLoadingTime", 0.5.seconds)

val AUTH_REFRESH_TOKEN_SETTINGS = StringBasedConfig("authRefreshToken", "")

val AUTH_ACCESS_TOKEN_SETTINGS = StringBasedConfig("authAccessToken", "")
