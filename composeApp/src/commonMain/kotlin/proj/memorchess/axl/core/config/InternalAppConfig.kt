package proj.memorchess.axl.core.config

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Delay after each move in training mode before loading the next move */
val TRAINING_MOVE_DELAY_SETTING =
  AppConfigItem(
    "trainingMoveDelay",
    1.seconds,
    { long -> long.milliseconds },
    { duration -> duration.inWholeMilliseconds },
  )
