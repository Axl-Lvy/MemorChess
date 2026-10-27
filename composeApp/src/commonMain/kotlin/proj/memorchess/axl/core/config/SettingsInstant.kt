package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Instant

/** Stores [instant] under [key] at millisecond precision. */
internal fun Settings.putInstant(key: String, instant: Instant) {
  putLong(key, instant.toEpochMilliseconds())
}

/** The instant [putInstant] stored under [key], or `null` when none was. */
internal fun Settings.getInstantOrNull(key: String): Instant? =
  getLongOrNull(key)?.let(Instant::fromEpochMilliseconds)
