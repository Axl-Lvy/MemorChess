package proj.memorchess.axl.core.auth

actual val SYNC_CLIENT_ID: String = "hgsu2itrlol8q7ey0w864"

// Reuses the same loopback port as LICHESS_REDIRECT_URI. Safe: OAuthLauncher.jvm.kt's HTTP
// server is one-shot per launch() call and torn down before the next one can start, and a user
// never runs two sign-in flows at once.
actual val SYNC_REDIRECT_URI: String = "http://127.0.0.1:9009/callback"
