package proj.memorchess.axl.core.sync

/**
 * The name this build reports when registering its device, one of [DevicePlatform].
 *
 * Mirrors [proj.memorchess.axl.core.config.getPlatformSpecificSettings]'s pattern: one `expect`
 * with an `actual` per source set, rather than a runtime lookup.
 */
internal expect fun currentPlatform(): String
