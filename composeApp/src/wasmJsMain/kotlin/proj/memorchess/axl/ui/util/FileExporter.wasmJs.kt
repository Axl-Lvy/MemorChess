package proj.memorchess.axl.ui.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download

/** Exports [content] via browser download. */
actual suspend fun exportToFile(content: String, baseName: String, extension: String) {
  FileKit.download(content.encodeToByteArray(), "$baseName.$extension")
}
