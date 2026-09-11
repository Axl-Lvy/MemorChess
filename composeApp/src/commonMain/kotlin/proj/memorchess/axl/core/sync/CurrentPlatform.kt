package proj.memorchess.axl.core.sync

/**
 * The platform this build runs on, reported when registering its device.
 *
 * Mirrors [proj.memorchess.axl.core.config.getPlatformSpecificSettings]'s pattern: one `expect`
 * with an `actual` per source set, rather than a runtime lookup.
 */
internal expect fun currentPlatform(): DevicePlatform
