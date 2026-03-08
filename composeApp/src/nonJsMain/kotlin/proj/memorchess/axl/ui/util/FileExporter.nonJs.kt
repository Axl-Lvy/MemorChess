package proj.memorchess.axl.ui.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.writeString

/** Exports [content] via a native save dialog. */
actual suspend fun exportToFile(content: String, baseName: String, extension: String) {
  val file = FileKit.openFileSaver(suggestedName = baseName, extension = extension)
  file?.writeString(content)
}
