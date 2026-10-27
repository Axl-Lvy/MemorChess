package proj.memorchess.axl.core.data.explorer

// The browser sends its own User-Agent, which Lichess accepts. Chrome drops an override, and
// Firefox's fails the explorer's CORS preflight, which does not allow a User-Agent header.
internal actual fun explorerUserAgent(): String? = null
