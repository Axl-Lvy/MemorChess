package proj.memorchess.axl.core.data.repertoire

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnImportPreview
import proj.memorchess.axl.core.pgn.PgnImportSummary

/**
 * Drives the repertoire library page.
 *
 * Exposes three flows that the UI renders: [catalogState] for the catalog list (loading, loaded
 * with staleness flag, or one error per failure mode), [installStates] for the per repertoire
 * install lifecycle, and [previewStates] for the on demand overlap shown before an install. All
 * mutations happen here so the composables stay free of business logic.
 *
 * The catalog and install collaborators are injected as suspending functions rather than concrete
 * classes so tests can substitute trivial fakes. Production wiring binds them to
 * [CachedRepertoireCatalog.getManifest], [RepertoireCatalogClient.fetchPgn],
 * [proj.memorchess.axl.core.pgn.PgnImporter.import] and
 * [proj.memorchess.axl.core.pgn.PgnImporter.preview].
 *
 * Reentrancy: [refresh] is ignored while a load is in flight, [install] is ignored only while a
 * fetch or import for the same repertoire is in flight (an installed repertoire may be
 * reinstalled), and [requestPreview] is ignored while a preview for the same repertoire is loading.
 * The guards flip state synchronously before any coroutine is launched, so a double tap on the same
 * frame is still coalesced.
 *
 * @param loadManifest Returns the catalog manifest, normally through the persisted cache.
 * @param fetchPgn Downloads and parses the PGN file at the given catalog relative path.
 * @param importGames Merges parsed games into the opening graph from the repertoire's
 *   [RepertoireColor] perspective and reports the summary. Any exception it throws is surfaced as
 *   [InstallError.ImportFailed] and the repertoire is not marked installed.
 * @param previewGames Computes, without writing anything, how much of the repertoire the user
 *   already has from the repertoire's [RepertoireColor] perspective.
 * @param installedStore Records which repertoires are installed on this device.
 * @param scope Scope tied to the screen's lifecycle (use `rememberCoroutineScope` in Compose).
 */
class RepertoireLibraryViewModel(
  private val loadManifest: suspend () -> CachedManifestResult,
  private val fetchPgn: suspend (file: String) -> CatalogResult<List<PgnGame>>,
  private val importGames:
    suspend (color: RepertoireColor, games: List<PgnGame>) -> PgnImportSummary,
  private val previewGames:
    suspend (color: RepertoireColor, games: List<PgnGame>) -> PgnImportPreview,
  private val installedStore: InstalledRepertoireStore,
  private val scope: CoroutineScope,
) {

  private val internalCatalogState =
    MutableStateFlow<LibraryCatalogState>(LibraryCatalogState.Loading)
  private val internalInstallStates =
    MutableStateFlow<Map<String, RepertoireInstallState>>(emptyMap())
  private val internalPreviewStates =
    MutableStateFlow<Map<String, RepertoirePreviewState>>(emptyMap())
  private var loadInFlight = false

  /** Current state of the catalog list. */
  val catalogState: StateFlow<LibraryCatalogState> = internalCatalogState.asStateFlow()

  /**
   * Install state of every repertoire of the loaded catalog, keyed by [RepertoireDescriptor.id]. A
   * missing key means [RepertoireInstallState.NotInstalled].
   */
  val installStates: StateFlow<Map<String, RepertoireInstallState>> =
    internalInstallStates.asStateFlow()

  /**
   * Overlap preview of every repertoire for which one was requested, keyed by
   * [RepertoireDescriptor.id]. A missing key means no preview has been requested yet.
   */
  val previewStates: StateFlow<Map<String, RepertoirePreviewState>> =
    internalPreviewStates.asStateFlow()

  init {
    refresh()
  }

  /** Reloads the catalog manifest. Ignored while a load is already in flight. */
  fun refresh() {
    if (loadInFlight) {
      return
    }
    loadInFlight = true
    internalCatalogState.value = LibraryCatalogState.Loading
    scope.launch {
      try {
        runLoad()
      } finally {
        loadInFlight = false
      }
    }
  }

  /**
   * Starts installing [descriptor]: fetches its PGN file, imports it into the opening graph, and
   * marks it installed on success only. Ignored only while a fetch or import for the same
   * repertoire is already in flight. An already installed repertoire may be reinstalled, which
   * restores any moves the user has since removed (the import skips the moves still present), and a
   * previously failed install may be retried.
   */
  fun install(descriptor: RepertoireDescriptor) {
    when (internalInstallStates.value[descriptor.id]) {
      is RepertoireInstallState.Fetching,
      is RepertoireInstallState.Importing -> return
      is RepertoireInstallState.Installed,
      is RepertoireInstallState.Failed,
      is RepertoireInstallState.NotInstalled,
      null -> Unit
    }
    setInstallState(descriptor.id, RepertoireInstallState.Fetching)
    scope.launch { runInstall(descriptor) }
  }

  /**
   * Computes how much of [descriptor] the user already has and publishes it on [previewStates], so
   * the install confirmation can show the overlap. Ignored while a preview for the same repertoire
   * is already loading; otherwise it always recomputes, because the graph may have changed since
   * the last preview.
   */
  fun requestPreview(descriptor: RepertoireDescriptor) {
    if (internalPreviewStates.value[descriptor.id] is RepertoirePreviewState.Loading) {
      return
    }
    setPreviewState(descriptor.id, RepertoirePreviewState.Loading)
    scope.launch { runPreview(descriptor) }
  }

  private suspend fun runLoad() {
    internalCatalogState.value =
      when (val result = loadManifest()) {
        is CachedManifestResult.Fresh -> loaded(result.manifest, isStale = false)
        is CachedManifestResult.Stale -> loaded(result.manifest, isStale = true)
        is CachedManifestResult.NetworkError -> LibraryCatalogState.NetworkError(result.message)
        is CachedManifestResult.HttpError -> LibraryCatalogState.HttpError(result.status)
        is CachedManifestResult.MalformedManifest ->
          LibraryCatalogState.MalformedManifest(result.message)
      }
  }

  /**
   * Builds the loaded state and seeds [installStates] for every listed repertoire: states of an
   * ongoing session (in flight installs, summaries, failures) are preserved, otherwise the
   * persisted installed set decides between installed and not installed.
   */
  private fun loaded(manifest: RepertoireManifest, isStale: Boolean): LibraryCatalogState.Loaded {
    val previous = internalInstallStates.value
    internalInstallStates.value =
      manifest.repertoires.associate { descriptor ->
        val prior = previous[descriptor.id]
        descriptor.id to
          when {
            prior != null && prior !is RepertoireInstallState.NotInstalled -> prior
            installedStore.isInstalled(descriptor.id) ->
              RepertoireInstallState.Installed(summary = null)
            else -> RepertoireInstallState.NotInstalled
          }
      }
    return LibraryCatalogState.Loaded(manifest.repertoires, isStale)
  }

  private suspend fun runInstall(descriptor: RepertoireDescriptor) {
    val games = fetchGames(descriptor) { fail(descriptor.id, it) } ?: return
    setInstallState(descriptor.id, RepertoireInstallState.Importing)
    val summary =
      try {
        val importSummary = importGames(descriptor.color, games)
        installedStore.markInstalled(descriptor.id)
        importSummary
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        LOGGER.w(e) { "Import of repertoire ${descriptor.id} failed" }
        fail(descriptor.id, InstallError.ImportFailed(e.message ?: "Import failed"))
        return
      }
    setInstallState(descriptor.id, RepertoireInstallState.Installed(summary))
  }

  private suspend fun runPreview(descriptor: RepertoireDescriptor) {
    val games =
      fetchGames(descriptor) { setPreviewState(descriptor.id, RepertoirePreviewState.Failed(it)) }
        ?: return
    val preview =
      try {
        previewGames(descriptor.color, games)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        LOGGER.w(e) { "Preview of repertoire ${descriptor.id} failed" }
        setPreviewState(
          descriptor.id,
          RepertoirePreviewState.Failed(InstallError.ImportFailed(e.message ?: "Preview failed")),
        )
        return
      }
    setPreviewState(descriptor.id, RepertoirePreviewState.Ready(preview))
  }

  /**
   * Downloads and parses the PGN of [descriptor], shared by install and preview. On a non success
   * fetch it reports the matching [InstallError] through [onError] and returns `null`; otherwise it
   * returns the parsed games. A PGN fetch never reports [CatalogResult.MalformedManifest], but it
   * is mapped defensively for exhaustiveness.
   */
  private suspend fun fetchGames(
    descriptor: RepertoireDescriptor,
    onError: (InstallError) -> Unit,
  ): List<PgnGame>? {
    val error =
      when (val result = fetchPgn(descriptor.file)) {
        is CatalogResult.Ok -> return result.value
        is CatalogResult.NetworkError -> InstallError.Network(result.message)
        is CatalogResult.HttpError -> InstallError.Http(result.status)
        is CatalogResult.MalformedPgn -> InstallError.MalformedPgn(result.message)
        is CatalogResult.MalformedManifest -> InstallError.MalformedPgn(result.message)
      }
    onError(error)
    return null
  }

  private fun fail(id: String, error: InstallError) {
    setInstallState(id, RepertoireInstallState.Failed(error))
  }

  private fun setInstallState(id: String, state: RepertoireInstallState) {
    internalInstallStates.value = internalInstallStates.value + (id to state)
  }

  private fun setPreviewState(id: String, state: RepertoirePreviewState) {
    internalPreviewStates.value = internalPreviewStates.value + (id to state)
  }
}

/** State of the catalog list. Consumed by Compose. */
sealed class LibraryCatalogState {

  /** The manifest is being loaded. */
  data object Loading : LibraryCatalogState()

  /**
   * The manifest is available. [isStale] is `true` when the network refresh failed and the list
   * comes from a cached manifest past its TTL. [repertoires] may be empty.
   */
  data class Loaded(val repertoires: List<RepertoireDescriptor>, val isStale: Boolean) :
    LibraryCatalogState()

  /** The request failed before an HTTP status was obtained and no cached manifest exists. */
  data class NetworkError(val message: String) : LibraryCatalogState()

  /** The server answered with a non success HTTP [status] and no cached manifest exists. */
  data class HttpError(val status: Int) : LibraryCatalogState()

  /** The downloaded manifest is invalid and no cached manifest exists. */
  data class MalformedManifest(val message: String) : LibraryCatalogState()
}

/** Install lifecycle of one catalog repertoire. Consumed by Compose. */
sealed class RepertoireInstallState {

  /** The repertoire is not installed and no install is running. */
  data object NotInstalled : RepertoireInstallState()

  /** The PGN file is being downloaded. */
  data object Fetching : RepertoireInstallState()

  /** The downloaded games are being merged into the opening graph. */
  data object Importing : RepertoireInstallState()

  /**
   * The repertoire is installed. [summary] is available when the install happened in this session
   * and `null` when the installed flag was restored from persistence.
   */
  data class Installed(val summary: PgnImportSummary?) : RepertoireInstallState()

  /** The last install attempt failed with [error]. The install may be retried. */
  data class Failed(val error: InstallError) : RepertoireInstallState()
}

/** On demand overlap of one catalog repertoire against the user's graph. Consumed by Compose. */
sealed class RepertoirePreviewState {

  /** The overlap is being computed (PGN download plus comparison). */
  data object Loading : RepertoirePreviewState()

  /** The overlap was computed: [preview] holds the repertoire size and the moves in common. */
  data class Ready(val preview: PgnImportPreview) : RepertoirePreviewState()

  /** The overlap could not be computed because of [error]. The install button stays usable. */
  data class Failed(val error: InstallError) : RepertoirePreviewState()
}

/** Reason an install attempt failed. */
sealed class InstallError {

  /** The PGN download failed before an HTTP status was obtained. */
  data class Network(val message: String) : InstallError()

  /** The server answered the PGN download with a non success HTTP [status]. */
  data class Http(val status: Int) : InstallError()

  /** The PGN file was downloaded but does not parse or contains no moves. */
  data class MalformedPgn(val message: String) : InstallError()

  /** The PGN parsed but could not be merged into the opening graph. Nothing was written. */
  data class ImportFailed(val message: String) : InstallError()
}

private val LOGGER = Logger.withTag("RepertoireLibraryViewModel")
