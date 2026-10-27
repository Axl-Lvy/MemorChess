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
 * Drives the repertoire library page: catalog list, per repertoire installs and overlap previews.
 * Reentrancy guards flip state before launching, so a same-frame double tap is coalesced.
 *
 * @param reloadManifest Fetches the manifest from the network, bypassing the cache age.
 * @param importMoves Any exception it throws fails the install, which is then not registered.
 * @param reportInstall Must never throw.
 * @param loadRepertoire `null` when the id is unregistered; a row without an installed version is
 *   the user's own repertoire.
 * @param uninstallRepertoire Deletes the moves nothing else holds.
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
  private val previewMoves:
    suspend (descriptor: RepertoireDescriptor, moves: List<RepertoireMove>) -> PgnImportPreview,
  private val reportInstall: suspend (id: String) -> Unit,
  private val loadRepertoire: suspend (id: String) -> DataRepertoire?,
  private val registerInstall: suspend (descriptor: RepertoireDescriptor) -> Unit,
  private val loadMyRepertoires: suspend () -> List<DataRepertoire>,
  private val loadMasterySnapshots: suspend () -> Map<String, RepertoireMasterySnapshot>,
  private val previewUninstall: suspend (id: String) -> Int,
  private val uninstallRepertoire: suspend (id: String) -> Unit,
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
  private val internalUninstallPreviews = MutableStateFlow<Map<String, Int>>(emptyMap())
  private val uninstallsInFlight = mutableSetOf<String>()
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

  /**
   * How many moves uninstalling each repertoire would delete, keyed by [RepertoireDescriptor.id]. A
   * missing key means the count has not been computed yet.
   */
  val uninstallPreviews: StateFlow<Map<String, Int>> = internalUninstallPreviews.asStateFlow()

  /** The user's own registered repertoires, for the "My Repertoires" list. */
  val myRepertoires: StateFlow<List<DataRepertoire>> = internalMyRepertoires.asStateFlow()

  /** Mastery snapshot of every registered repertoire, keyed by repertoire id. */
  val masterySnapshots: StateFlow<Map<String, RepertoireMasterySnapshot>> =
    internalMasterySnapshots.asStateFlow()

  init {
    refresh()
    refreshMyRepertoires()
  }

  /** Reloads [myRepertoires] and [masterySnapshots]. */
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
   * Installs [descriptor] into the opening graph, failing with [InstallError.IdTaken] if a user
   * repertoire owns the id. Ignored while the same repertoire is fetching or importing.
   */
  fun install(descriptor: RepertoireDescriptor) {
    if (descriptor.id in uninstallsInFlight) return
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
   * Publishes on [previewStates] how much of [descriptor] the user already has. Ignored while the
   * same preview is loading; otherwise always recomputes, since the graph may have changed.
   */
  fun requestPreview(descriptor: RepertoireDescriptor) {
    if (internalPreviewStates.value[descriptor.id] is RepertoirePreviewState.Loading) {
      return
    }
    setPreviewState(descriptor.id, RepertoirePreviewState.Loading)
    scope.launch { runPreview(descriptor) }
  }

  /** Computes how many moves uninstalling the installed repertoire [id] would delete. */
  fun requestUninstallPreview(id: String) {
    internalUninstallPreviews.value -= id
    scope.launch { internalUninstallPreviews.value += id to previewUninstall(id) }
  }

  /**
   * Uninstalls the installed repertoire [id], listed in the catalog or not, then marks it not
   * installed. Ignored while an install or an uninstall of it is running; a failed uninstall leaves
   * it installed.
   */
  fun uninstall(id: String) {
    when (internalInstallStates.value[id]) {
      is RepertoireInstallState.Fetching,
      is RepertoireInstallState.Importing -> return
      is RepertoireInstallState.Installed,
      is RepertoireInstallState.Failed,
      is RepertoireInstallState.NotInstalled,
      null -> Unit
    }
    if (!uninstallsInFlight.add(id)) return
    scope.launch {
      try {
        uninstallRepertoire(id)
        setInstallState(id, RepertoireInstallState.NotInstalled)
        internalPreviewStates.value -= id
        internalUninstallPreviews.value -= id
        refreshMyRepertoires()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        LOGGER.w(e) { "Uninstall of repertoire $id failed" }
      } finally {
        uninstallsInFlight -= id
      }
    }
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

  /** Builds the loaded state, keeping this session's install states over the persisted ones. */
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
            else ->
              when (val version = loadRepertoire(descriptor.id)?.installedVersion) {
                null -> RepertoireInstallState.NotInstalled
                else ->
                  RepertoireInstallState.Installed(
                    summary = null,
                    isOutdated = version < descriptor.version,
                  )
              }
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
    // Published before the telemetry round trip so the bar never waits at 100% under "Importing…".
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
        previewMoves(descriptor, moves)
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
   * Downloads [descriptor]'s moves, or reports the [InstallError] to [onError] and returns `null`.
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

/** State of the catalog list. */
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

/** Install lifecycle of one catalog repertoire. */
sealed class RepertoireInstallState {

  /** The repertoire is not installed and no install is running. */
  data object NotInstalled : RepertoireInstallState()

  /** The moves are being downloaded. [fraction] is the `0f`..`1f` byte progress. */
  data class Fetching(val fraction: Float) : RepertoireInstallState()

  /**
   * The moves are being merged into the opening graph. [fraction] is `0f`..`1f` and reaches `1f`
   * only once the write itself has completed.
   */
  data class Importing(val fraction: Float) : RepertoireInstallState()

  /**
   * The repertoire is installed. [summary] is available when the install happened in this session
   * and `null` when the installed flag was restored from persistence. [isOutdated] is `true` when
   * the catalog lists a newer version than the installed one.
   */
  data class Installed(val summary: PgnImportSummary?, val isOutdated: Boolean = false) :
    RepertoireInstallState()

  /** The last install attempt failed with [error]. The install may be retried. */
  data class Failed(val error: InstallError) : RepertoireInstallState()
}

/** On demand overlap of one catalog repertoire against the user's graph. */
sealed class RepertoirePreviewState {

  /** The overlap is being computed. */
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
