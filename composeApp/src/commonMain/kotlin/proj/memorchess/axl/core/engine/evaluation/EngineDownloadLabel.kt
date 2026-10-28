package proj.memorchess.axl.core.engine.evaluation

import fr.axl_lvy.stockfish_multiplatform.EngineDownload

/** Short label such as `"42%"` while the engine downloads, or `null` when there is no download. */
internal fun engineDownloadLabel(download: EngineDownload): String? =
  when (download) {
    is EngineDownload.InProgress -> "${(download.fraction * 100).toInt()}%"
    EngineDownload.NotStarted,
    EngineDownload.Done,
    is EngineDownload.Failed -> null
  }
