package proj.memorchess.axl.core.data.repertoire

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.RepertoireMasterySnapshot
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
 * [CachedRepertoireCatalog.getManifest], [RepertoireCatalogClient.fetchMoves],
 * [proj.memorchess.axl.core.graph.RepertoireMoveImporter.import],
 * [proj.memorchess.axl.core.graph.RepertoireMoveImporter.preview] and
 * [RepertoireCatalogClient.reportInstall].
 *
 * Reentrancy: [refresh] is ignored while a load is in flight, [install] is ignored only while a
 * fetch or import for the same repertoire is in flight (an installed repertoire may be
 * reinstalled), and [requestPreview] is ignored while a preview for the same repertoire is loading.
 * The guards flip state synchronously before any coroutine is launched, so a double tap on the same
 * frame is still coalesced.
 *
 * @param loadManifest Returns the catalog manifest, normally through the persisted cache.
 * @param reloadManifest Returns the catalog manifest fetched from the network now, used by
 *   [forceRefresh]; production wiring binds [CachedRepertoireCatalog.refresh].
 * @param fetchMoves Downloads the moves of the given repertoire's listed version, reporting
 *   download progress (`0f`..`1f`) through its second parameter as
 *   [RepertoireInstallState.Fetching] is updated. May never call it at all, see
 *   [RepertoireCatalogClient.fetchMoves]'s own KDoc.
 * @param importMoves Installs the downloaded moves into the opening graph as holds of the repertoire
 *   named by `descriptor`, and reports the summary. Any exception it throws is surfaced as
 *   [InstallError.ImportFailed] and the repertoire is not registered as installed. Reports import
 *   progress (`0f`..`1f`) through its third parameter as [RepertoireInstallState.Importing] is
 *   updated.
 * @param previewMoves Computes, without writing anything, how much of the repertoire the user
 *   already has.
 * @param reportInstall Reports one completed first install of the given repertoire id, feeding the
 *   catalog's anonymous popularity counter (see [RepertoireDescriptor.downloadCount]'s KDoc). Must
 *   never throw; production wiring binds [RepertoireCatalogClient.reportInstall], which satisfies
 *   that by design.
 * @param loadRepertoire Returns the synced registry row of the given id, or `null` when none
 *   exists; a row with an installed version is an installed repertoire, one without is the user's
 *   own.
 * @param registerInstall Registers the given repertoire as installed at its listed version, after
 *   a successful import.
 * @param loadMyRepertoires Returns the user's own registered repertoires (the tagging registry),
 *   normally through [proj.memorchess.axl.core.graph.RepertoireTagStore.repertoires].
 * @param loadMasterySnapshots Returns the mastery snapshot of every registered repertoire, keyed by
 *   repertoire id, normally through
 *   [proj.memorchess.axl.core.graph.RepertoireTagStore.masterySnapshots].
 * @param scope Scope tied to the screen's lifecycle (use `rememberCoroutineScope` in Compose).
 */
class RepertoireLibraryViewModel(
  private val loadManifest: suspend () -> CachedManifestResult,
  private val reloadManifest: suspend () -> CachedManifestResult = loadManifest,
  private val fetchMoves:
    suspend (
      descriptor: RepertoireDescriptor,
      onProgress: (Float) -> Unit,
    ) -> CatalogResult<List<RepertoireMove>>,
  private val importMoves:
    suspend (
      descriptor: RepertoireDescriptor,
      moves: List<RepertoireMove>,
      onProgress: (Float) -> Unit,
    ) -> PgnImportSummary,
  private val previewMoves: suspend (moves: List<RepertoireMove>) -> PgnImportPreview,
  private val reportInstall: suspend (id: String) -> Unit,
  private val loadRepertoire: suspend (id: String) -> DataRepertoire?,
  private val registerInstall: suspend (descriptor: RepertoireDescriptor) -> Unit,
  private val loadMyRepertoires: suspend () -> List<DataRepertoire>,
  private val loadMasterySnapshots: suspend () -> Map<String, RepertoireMasterySnapshot>,
  private val scope: CoroutineScope,
) {

  private val internalCatalogState =
    MutableStateFlow<LibraryCatalogState>(LibraryCatalogState.Loading)
  private val internalInstallStates =
    MutableStateFlow<Map<String, RepertoireInstallState>>(emptyMap())
  private val internalPreviewStates =
    MutableStateFlow<Map<String, RepertoirePreviewState>>(emptyMap())
  private val internalMyRepertoires = MutableStateFlow<List<DataRepertoire>>(emptyList())
  private val internalMasterySnapshots =
    MutableStateFlow<Map<String, RepertoireMasterySnapshot>>(emptyMap())
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

  /** The user's own registered repertoires, for the "My Repertoires" list. */
  val myRepertoires: StateFlow<List<DataRepertoire>> = internalMyRepertoires.asStateFlow()

  /** Mastery snapshot of every registered repertoire, keyed by repertoire id. */
  val masterySnapshots: StateFlow<Map<String, RepertoireMasterySnapshot>> =
    internalMasterySnapshots.asStateFlow()

  init {
    refresh()
    refreshMyRepertoires()
  }

  /**
   * Reloads [myRepertoires] and [masterySnapshots]. Called on construction, and again after a
   * create or fork completes so the new repertoire shows up without leaving the page.
   */
  fun refreshMyRepertoires() {
    scope.launch { internalMyRepertoires.value = loadMyRepertoires() }
    refreshMasterySnapshots()
  }

  private fun refreshMasterySnapshots() {
    scope.launch { internalMasterySnapshots.value = loadMasterySnapshots() }
  }

  /** Reloads the catalog manifest. Ignored while a load is already in flight. */
  fun refresh() {
    startLoad(loadManifest)
  }

  /**
   * Refetches the catalog from the network regardless of the cache age, and reloads
   * [myRepertoires]. Ignored while a load is already in flight.
   */
  fun forceRefresh() {
    if (startLoad(reloadManifest)) {
      refreshMyRepertoires()
    }
  }

  /** Returns `false` without doing anything when a load is already in flight. */
  private fun startLoad(load: suspend () -> CachedManifestResult): Boolean {
    if (loadInFlight) {
      return false
    }
    loadInFlight = true
    internalCatalogState.value = LibraryCatalogState.Loading
    scope.launch {
      try {
        runLoad(load)
      } finally {
        loadInFlight = false
      }
    }
    return true
  }

  /**
   * Starts installing [descriptor]: fetches its moves, installs them into the opening graph, and
   * registers it as installed on success only. Fails with [InstallError.IdTaken] when one of the
   * user's own repertoires already uses the id. Ignored only while a fetch or import for the same
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
    setInstallState(descriptor.id, RepertoireInstallState.Fetching(0f))
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

  private suspend fun runLoad(load: suspend () -> CachedManifestResult) {
    internalCatalogState.value =
      when (val result = load()) {
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
  private suspend fun loaded(
    manifest: RepertoireManifest,
    isStale: Boolean,
  ): LibraryCatalogState.Loaded {
    val previous = internalInstallStates.value
    internalInstallStates.value =
      manifest.repertoires.associate { descriptor ->
        val prior = previous[descriptor.id]
        descriptor.id to
          when {
            prior != null && prior !is RepertoireInstallState.NotInstalled -> prior
            loadRepertoire(descriptor.id)?.installedVersion != null ->
              RepertoireInstallState.Installed(summary = null)
            else -> RepertoireInstallState.NotInstalled
          }
      }
    return LibraryCatalogState.Loaded(manifest.repertoires, isStale)
  }

  private suspend fun runInstall(descriptor: RepertoireDescriptor) {
    val registered = loadRepertoire(descriptor.id)
    if (registered != null && registered.installedVersion == null) {
      fail(descriptor.id, InstallError.IdTaken)
      return
    }
    val moves =
      fetchRepertoire(
        descriptor,
        onProgress = { setInstallState(descriptor.id, RepertoireInstallState.Fetching(it)) },
      ) {
        fail(descriptor.id, it)
      } ?: return
    setInstallState(descriptor.id, RepertoireInstallState.Importing(0f))
    // A reinstall of an already installed repertoire is never counted as a second install of it.
    val isFirstInstall = registered == null
    val summary =
      try {
        val importSummary =
          importMoves(descriptor, moves) {
            setInstallState(descriptor.id, RepertoireInstallState.Importing(it))
          }
        registerInstall(descriptor)
        importSummary
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        LOGGER.w(e) { "Import of repertoire ${descriptor.id} failed" }
        fail(descriptor.id, InstallError.ImportFailed(e.message ?: "Import failed"))
        return
      }
    // Installed is published before the best-effort telemetry call below, not after: reportInstall
    // suspends on a network round trip, and the bar must never sit at 100% under "Importing…"
    // waiting on that when the install itself is already done.
    setInstallState(descriptor.id, RepertoireInstallState.Installed(summary))
    refreshMasterySnapshots()
    if (isFirstInstall) {
      reportInstall(descriptor.id)
    }
  }

  private suspend fun runPreview(descriptor: RepertoireDescriptor) {
    val moves =
      fetchRepertoire(descriptor) {
        setPreviewState(descriptor.id, RepertoirePreviewState.Failed(it))
      } ?: return
    val preview =
      try {
        previewMoves(moves)
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
   * Downloads the moves of [descriptor], shared by install and preview. On a non success fetch it
   * reports the matching [InstallError] through [onError] and returns `null`; otherwise it returns
   * the moves. A moves fetch never reports [CatalogResult.MalformedManifest], but it is mapped
   * defensively for exhaustiveness.
   *
   * @param onProgress Forwarded to [fetchMoves]. Defaults to a no-op: [runPreview] has no progress
   *   bar to drive, only [runInstall] passes one.
   */
  private suspend fun fetchRepertoire(
    descriptor: RepertoireDescriptor,
    onProgress: (Float) -> Unit = {},
    onError: (InstallError) -> Unit,
  ): List<RepertoireMove>? {
    val error =
      when (val result = fetchMoves(descriptor, onProgress)) {
        is CatalogResult.Ok -> return result.value
        is CatalogResult.NetworkError -> InstallError.Network(result.message)
        is CatalogResult.HttpError -> InstallError.Http(result.status)
        is CatalogResult.MalformedRepertoire -> InstallError.MalformedRepertoire(result.message)
        is CatalogResult.MalformedManifest -> InstallError.MalformedRepertoire(result.message)
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

  /** The PGN file is being downloaded. [fraction] is the download's `0f`..`1f` byte progress. */
  data class Fetching(val fraction: Float) : RepertoireInstallState()

  /**
   * The downloaded games are being merged into the opening graph. [fraction] is the `0f`..`1f`
   * progress reported by [proj.memorchess.axl.core.pgn.PgnImporter.import]'s `onProgress` parameter
   * (see its KDoc for the exact contract): it reaches `1f` only once the write itself has
   * completed, not merely once every game is planned.
   */
  data class Importing(val fraction: Float) : RepertoireInstallState()

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

  /** The download failed before an HTTP status was obtained. */
  data class Network(val message: String) : InstallError()

  /** The server answered the download with a non success HTTP [status]. */
  data class Http(val status: Int) : InstallError()

  /** The repertoire was downloaded but does not decode or contains no moves. */
  data class MalformedRepertoire(val message: String) : InstallError()

  /** The repertoire decoded but could not be merged into the opening graph. Nothing was written. */
  data class ImportFailed(val message: String) : InstallError()

  /** One of the user's own repertoires already uses the catalog id. Nothing was written. */
  data object IdTaken : InstallError()
}

private val LOGGER = Logger.withTag("RepertoireLibraryViewModel")
