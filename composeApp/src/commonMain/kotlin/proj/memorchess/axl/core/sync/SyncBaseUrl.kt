package proj.memorchess.axl.core.sync

/**
 * Root URL of the deployed `:server`, fronting both `/v1/sync` and `/v1/repertoires`. Native
 * platforms hardcode the mini PC domain; wasmJs derives it from the page origin so local dev
 * against `:composeApp:wasmJsRun`, which doesn't serve from that domain, keeps working unchanged.
 */
expect val SYNC_BASE_URL: String
