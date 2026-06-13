package proj.memorchess.axl.core.data.study

import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.core.pgn.PgnImporter

/**
 * End to end flow that imports a Lichess study into the opening graph.
 *
 * Downloads the study with [LichessStudyClient], reloads [treeStore] from disk so the merge sees
 * the persisted graph, then merges every chapter through [PgnImporter]. The importer validates the
 * whole study before writing, so a failed import leaves the graph exactly as it was.
 *
 * @property client Downloads and parses the study export.
 * @property treeStore Mutation chokepoint of the opening graph that receives the imported moves.
 */
class LichessStudyImporter(
  private val client: LichessStudyClient,
  private val treeStore: TreeStore,
) {

  private val pgnImporter = PgnImporter(treeStore)

  /**
   * Imports the study referenced by [input].
   *
   * @param input A study URL or a bare study id.
   * @return A typed result the UI can render without inspecting exceptions.
   */
  suspend fun import(input: String): LichessStudyImportResult =
    when (val fetched = client.fetchStudy(input)) {
      is LichessStudyResult.Ok ->
        try {
          treeStore.load()
          LichessStudyImportResult.Success(pgnImporter.import(fetched.games))
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
