package proj.memorchess.axl.core.data.repertoire

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnParseException
import proj.memorchess.axl.core.pgn.PgnParser

/** Outcome of a [RepertoireCreationViewModel.create] or [RepertoireCreationViewModel.fork] call. */
sealed interface CreationState {
  /** Nothing submitted yet, or the dialog was reset for a fresh attempt. */
  data object Idle : CreationState

  /** A create or fork is running. */
  data object Working : CreationState

  /** The attempt failed with [error]; nothing was written. */
  data class Failed(val error: CreationError) : CreationState

  /** The repertoire was created (or forked) under [repertoireId]. */
  data class Done(val repertoireId: String) : CreationState
}

/** Why a create or fork attempt failed. */
sealed interface CreationError {
  /** The given name is blank, or slugifies to nothing usable as an id. */
  data object BlankName : CreationError

  /** The slugified id is already registered. */
  data object DuplicateId : CreationError

  /** The pasted PGN could not be parsed or imported; [reason] is the underlying message. */
  data class InvalidPgn(val reason: String) : CreationError
}

/**
 * Drives the "create a new repertoire" and "fork an existing repertoire" dialogs.
 *
 * Both flows slugify the given name into an id (see [slugify]), rejecting a blank name or one that
 * collides with an id from [existingIds]. [create] registers the repertoire directly, or, when
 * [pgnText] is not blank, parses and imports it first and only registers once that succeeds — so a
 * malformed paste never leaves an empty repertoire behind. [fork] copies [forkRepertoire]'s tagged
 * edges from an existing repertoire, registering the new one as part of that call.
 *
 * The collaborators are injected as suspending functions, mirroring `RepertoireLibraryViewModel`
 * and `RepertoirePublishViewModel`, so tests can substitute trivial fakes. Production wiring binds
 * [proj.memorchess.axl.core.graph.RepertoireTagStore.repertoires] (mapped to ids),
 * [proj.memorchess.axl.core.graph.RepertoireTagStore.register],
 * [proj.memorchess.axl.core.pgn.PgnImporter.import], and
 * [proj.memorchess.axl.core.graph.RepertoireTagStore.fork].
 *
 * @param scope Scope tied to the dialog's lifecycle (use `rememberCoroutineScope` in Compose).
 */
class RepertoireCreationViewModel(
  private val existingIds: suspend () -> Set<String>,
  private val registerRepertoire:
    suspend (id: String, name: String, color: RepertoireColor?) -> Unit,
  private val importGames:
    suspend (repertoireId: String, color: RepertoireColor?, games: List<PgnGame>) -> Unit,
  private val forkRepertoire:
    suspend (sourceId: String, newId: String, newName: String, color: RepertoireColor?) -> Unit,
  private val scope: CoroutineScope,
) {

  private val internalState = MutableStateFlow<CreationState>(CreationState.Idle)

  /** Current lifecycle state of the last [create] or [fork] call. */
  val state: StateFlow<CreationState> = internalState.asStateFlow()

  /** Resets to [CreationState.Idle], for a dialog reopened after a previous attempt. */
  fun reset() {
    internalState.value = CreationState.Idle
  }

  /**
   * Creates a new repertoire named [name] in [color], optionally seeded from pasted PGN
   * ([pgnText]). See this class's own KDoc for the order of operations.
   */
  fun create(name: String, color: RepertoireColor?, pgnText: String) {
    internalState.value = CreationState.Working
    scope.launch { runCreate(name, color, pgnText) }
  }

  /** Forks [sourceId] into a new repertoire named [newName] in [color]. */
  fun fork(sourceId: String, newName: String, color: RepertoireColor?) {
    internalState.value = CreationState.Working
    scope.launch { runFork(sourceId, newName, color) }
  }

  private suspend fun runCreate(name: String, color: RepertoireColor?, pgnText: String) {
    val id = validatedSlug(name) ?: return
    val trimmedPgn = pgnText.trim()
    if (trimmedPgn.isNotEmpty()) {
      val games =
        try {
          PgnParser.parse(trimmedPgn)
        } catch (e: PgnParseException) {
          fail(e.message)
          return
        }
      try {
        importGames(id, color, games)
      } catch (e: PgnImportException) {
        fail(e.message)
        return
      }
    }
    registerRepertoire(id, name.trim(), color)
    internalState.value = CreationState.Done(id)
  }

  private suspend fun runFork(sourceId: String, newName: String, color: RepertoireColor?) {
    val id = validatedSlug(newName) ?: return
    forkRepertoire(sourceId, id, newName.trim(), color)
    internalState.value = CreationState.Done(id)
  }

  private fun fail(reason: String?) {
    internalState.value = CreationState.Failed(CreationError.InvalidPgn(reason.orEmpty()))
  }

  /**
   * Slugifies [name] into a fresh, unused id, or sets [internalState] to [CreationState.Failed] and
   * returns `null` when [name] is blank, slugifies to nothing, or collides with [existingIds].
   */
  private suspend fun validatedSlug(name: String): String? {
    val trimmed = name.trim()
    val id = slugify(trimmed)
    if (id.isEmpty()) {
      internalState.value = CreationState.Failed(CreationError.BlankName)
      return null
    }
    if (id in existingIds()) {
      internalState.value = CreationState.Failed(CreationError.DuplicateId)
      return null
    }
    return id
  }
}
