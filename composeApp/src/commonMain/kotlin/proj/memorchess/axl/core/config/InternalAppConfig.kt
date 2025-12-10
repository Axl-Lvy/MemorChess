package proj.memorchess.axl.core.config

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Minimum time to wait before loading page */
val MINIMUM_LOADING_TIME_SETTING = DurationBasedConfigItem("minimumLoadingTime", 0.5.seconds)

val AUTH_REFRESH_TOKEN_SETTINGS = StringBasedConfig("authRefreshToken", "")

val AUTH_ACCESS_TOKEN_SETTINGS = StringBasedConfig("authAccessToken", "")

val LAST_SYNC_TIME_SETTINGS = TimeBasedConfig("lastSyncTime", Instant.DISTANT_PAST)
