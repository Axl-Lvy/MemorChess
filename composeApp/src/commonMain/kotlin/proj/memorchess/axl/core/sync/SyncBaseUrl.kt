package proj.memorchess.axl.core.sync

/**
 * Root URL of the deployed `:server`. wasmJs derives it from the page origin so local dev against
 * `:composeApp:wasmJsRun` keeps working.
 */
expect val SYNC_BASE_URL: String
