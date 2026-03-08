package proj.memorchess.axl.ui.util

/** Exports [content] to a file via platform-specific save mechanism. */
expect suspend fun exportToFile(content: String, baseName: String, extension: String)
