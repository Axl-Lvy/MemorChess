package proj.memorchess.axl.core.auth

actual val SYNC_CLIENT_ID: String = "hgsu2itrlol8q7ey0w864"

// Shares LICHESS_REDIRECT_URI's port: the loopback server is one-shot per launch() and torn down
// before the next sign-in flow can start.
actual val SYNC_REDIRECT_URI: String = "http://127.0.0.1:9009/callback"
