package proj.memorchess.axl.core.data.explorer

// A browser owns the wire User-Agent. Chrome silently drops a script supplied override. Firefox
// sends it as requested, which then fails explorer.lichess.ovh's CORS preflight: its
// Access-Control-Allow-Headers lists Authorization but not User-Agent. Sending none here avoids
// that. A real browser's own User-Agent is never the generic library one Lichess rejects.
internal actual fun explorerUserAgent(): String? = null
