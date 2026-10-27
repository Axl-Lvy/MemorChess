package proj.memorchess.axl.core.data.study

import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.core.pgn.PgnImporter

/**
 * Imports a Lichess study into the opening graph. The whole study is validated before writing, so a
 * failed import leaves the graph untouched.
 */
class LichessStudyImporter(
  private val client: LichessStudyClient,
  private val treeStore: TreeStore,
  private val tagStore: RepertoireTagStore,
) {

  private val pgnImporter = PgnImporter(treeStore, tagStore)

  /**
   * Imports the study referenced by [input], a study URL or a bare study id. Tags every imported
   * move with [repertoireId] unless it is `null`.
   */
  suspend fun import(input: String, repertoireId: String? = null): LichessStudyImportResult =
    when (val fetched = client.fetchStudy(input)) {
      is LichessStudyResult.Ok ->
        try {
          LichessStudyImportResult.Success(
            pgnImporter.import(fetched.games, repertoireId = repertoireId)
          )
        } catch (e: PgnImportException) {
          LichessStudyImportResult.ImportFailed(e.message ?: "Import failed")
        }
      is LichessStudyResult.Error -> LichessStudyImportResult.FetchFailed(fetched)
    }
}

/** Result of a [LichessStudyImporter.import] call. */
sealed class LichessStudyImportResult {

  /** The study was downloaded and merged into the opening graph. */
  data class Success(val summary: PgnImportSummary) : LichessStudyImportResult()

  /** The study could not be downloaded or parsed. Nothing was written. */
  data class FetchFailed(val error: LichessStudyResult.Error) : LichessStudyImportResult()

  /** The study contains a move the engine rejects. Nothing was written. */
  data class ImportFailed(val message: String) : LichessStudyImportResult()
}
