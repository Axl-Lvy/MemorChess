package proj.memorchess.axl.core.config

import kotlin.time.Duration.Companion.seconds

/** Minimum time to wait before loading page */
val MINIMUM_LOADING_TIME_SETTING = DurationBasedConfigItem("minimumLoadingTime", 0.5.seconds)

/** Persistent user UUID used to identify the user to the server. */
val USER_ID_SETTING = StringBasedConfig("memorchessUserId", "")
