package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.RepertoireMasterySnapshot
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportPreview
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.test_util.TestWithKoin

@OptIn(ExperimentalCoroutinesApi::class)
class TestRepertoireLibraryViewModel : TestWithKoin() {

  private val london =
    RepertoireDescriptor(
      id = "london-system-white",
      name = "London System",
      color = RepertoireColor.WHITE,
      description = "Solid.",
      moveCount = 73,
      version = 1,
    )

  private val caroKann =
    RepertoireDescriptor(
      id = "caro-kann-black",
      name = "Caro Kann",
      color = RepertoireColor.BLACK,
      description = "Sturdy.",
      moveCount = 0,
      version = 1,
    )

  private fun manifest(vararg descriptors: RepertoireDescriptor) =
    RepertoireManifest(schemaVersion = 2, repertoires = descriptors.toList())

  private fun TestScope.buildViewModel(
    loadManifest: suspend () -> CachedManifestResult,
    reloadManifest: suspend () -> CachedManifestResult = loadManifest,
    fetchMoves:
      suspend (RepertoireDescriptor, (Float) -> Unit) -> CatalogResult<List<RepertoireMove>> =
      { _, _ ->
        CatalogResult.Ok(emptyList())
      },
    importMoves:
      suspend (RepertoireDescriptor, List<RepertoireMove>, (Float) -> Unit) -> PgnImportSummary =
      { _, _, _ ->
        PgnImportSummary(movesAdded = 3, movesAlreadyPresent = 1)
      },
    previewMoves: suspend (RepertoireDescriptor, List<RepertoireMove>) -> PgnImportPreview =
      { _, _ ->
        PgnImportPreview(totalMoves = 4, movesInCommon = 1)
      },
    reportInstall: suspend (String) -> Unit = {},
    store: FakeRegistry = FakeRegistry(),
    loadMyRepertoires: suspend () -> List<DataRepertoire> = { emptyList() },
    loadMasterySnapshots: suspend () -> Map<String, RepertoireMasterySnapshot> = { emptyMap() },
    previewUninstall: suspend (String) -> Int = { 0 },
    uninstallRepertoire: suspend (String) -> Unit = { store.rows.remove(it) },
  ) =
    RepertoireLibraryViewModel(
      loadManifest = loadManifest,
      reloadManifest = reloadManifest,
      fetchMoves = fetchMoves,
      importMoves = importMoves,
      previewMoves = previewMoves,
      reportInstall = reportInstall,
      loadRepertoire = { store.rows[it] },
      registerInstall = { descriptor ->
        store.rows[descriptor.id] =
          DataRepertoire(
            descriptor.id,
            descriptor.name,
            descriptor.color,
            installedVersion = descriptor.version,
          )
      },
      loadMyRepertoires = loadMyRepertoires,
      loadMasterySnapshots = loadMasterySnapshots,
      previewUninstall = previewUninstall,
      uninstallRepertoire = uninstallRepertoire,
      scope = backgroundScope,
    )

  @Test
  fun loadSuccessExposesRepertoiresAsFresh() = test {
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest(london, caroKann)) })

    val loaded =
      viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
        as LibraryCatalogState.Loaded

    loaded.repertoires shouldBe listOf(london, caroKann)
    loaded.isStale shouldBe false
  }

  @Test
  fun staleManifestPropagatesStaleFlag() = test {
    val viewModel = buildViewModel({ CachedManifestResult.Stale(manifest(london)) })

    val loaded =
      viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
        as LibraryCatalogState.Loaded

    loaded.repertoires shouldBe listOf(london)
    loaded.isStale shouldBe true
  }

  @Test
  fun emptyCatalogLoadsWithNoRepertoires() = test {
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest()) })

    val loaded =
      viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
        as LibraryCatalogState.Loaded

    loaded.repertoires shouldBe emptyList()
    viewModel.installStates.value shouldBe emptyMap()
  }

  @Test
  fun networkErrorWithoutCacheIsExposed() = test {
    val viewModel = buildViewModel({ CachedManifestResult.NetworkError("connection refused") })

    val state = viewModel.catalogState.first { it !is LibraryCatalogState.Loading }

    state shouldBe LibraryCatalogState.NetworkError("connection refused")
  }

  @Test
  fun httpErrorWithoutCacheIsExposed() = test {
    val viewModel = buildViewModel({ CachedManifestResult.HttpError(404) })

    val state = viewModel.catalogState.first { it !is LibraryCatalogState.Loading }

    state shouldBe LibraryCatalogState.HttpError(404)
  }

  @Test
  fun malformedManifestIsExposed() = test {
    val viewModel = buildViewModel({ CachedManifestResult.MalformedManifest("bad json") })

    val state = viewModel.catalogState.first { it !is LibraryCatalogState.Loading }

    state shouldBe LibraryCatalogState.MalformedManifest("bad json")
  }

  @Test
  fun refreshAfterErrorReloadsTheCatalog() = test {
    var manifestResult: CachedManifestResult = CachedManifestResult.NetworkError("offline")
    val viewModel = buildViewModel({ manifestResult })
    viewModel.catalogState.first { it !is LibraryCatalogState.Loading }

    manifestResult = CachedManifestResult.Fresh(manifest(london))
    viewModel.refresh()

    val loaded =
      viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
        as LibraryCatalogState.Loaded
    loaded.repertoires shouldBe listOf(london)
  }

  @Test
  fun myRepertoiresIsPopulatedFromTheInjectedLoaderOnConstruction() = test {
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest()) },
        loadMyRepertoires = {
          listOf(
            DataRepertoire(
              id = "italian-game",
              name = "Italian Game",
              color = RepertoireColor.WHITE,
            )
          )
        },
      )

    val loaded = viewModel.myRepertoires.first { it.isNotEmpty() }

    loaded.map { it.id } shouldBe listOf("italian-game")
  }

  @Test
  fun refreshMyRepertoiresReloadsFromTheInjectedLoader() = test {
    var currentIds = listOf("italian-game")
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest()) },
        loadMyRepertoires = {
          currentIds.map { DataRepertoire(id = it, name = it, color = RepertoireColor.WHITE) }
        },
      )
    viewModel.myRepertoires.first { it.isNotEmpty() }
    currentIds = listOf("italian-game", "italian-game-copy")

    viewModel.refreshMyRepertoires()
    val loaded = viewModel.myRepertoires.first { it.size == 2 }

    loaded.map { it.id } shouldBe listOf("italian-game", "italian-game-copy")
  }

  @Test
  fun masterySnapshotsArePopulatedFromTheInjectedLoaderOnConstruction() = test {
    val snapshot = RepertoireMasterySnapshot(solidCount = 2, totalCount = 7, lastReview = null)
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest()) },
        loadMasterySnapshots = { mapOf(london.id to snapshot) },
      )

    val loaded = viewModel.masterySnapshots.first { it.isNotEmpty() }

    loaded shouldBe mapOf(london.id to snapshot)
  }

  @Test
  fun masterySnapshotsAreReloadedAfterASuccessfulInstall() = test {
    var current = emptyMap<String, RepertoireMasterySnapshot>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        loadMasterySnapshots = { current },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
    advanceUntilIdle()
    val snapshot = RepertoireMasterySnapshot(solidCount = 0, totalCount = 12, lastReview = null)
    current = mapOf(london.id to snapshot)

    viewModel.install(london)
    val loaded = viewModel.masterySnapshots.first { it.isNotEmpty() }

    loaded shouldBe mapOf(london.id to snapshot)
  }

  @Test
  fun masterySnapshotsAreNotReloadedAfterAFailedInstall() = test {
    var loads = 0
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.NetworkError("offline") },
        loadMasterySnapshots = {
          loads++
          emptyMap()
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
    advanceUntilIdle()

    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }
    advanceUntilIdle()

    loads shouldBe 1
  }

  @Test
  fun forceRefreshAlsoReloadsMasterySnapshots() = test {
    var current = emptyMap<String, RepertoireMasterySnapshot>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest()) },
        loadMasterySnapshots = { current },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
    advanceUntilIdle()
    val snapshot = RepertoireMasterySnapshot(solidCount = 3, totalCount = 3, lastReview = null)
    current = mapOf(london.id to snapshot)

    viewModel.forceRefresh()
    val loaded = viewModel.masterySnapshots.first { it.isNotEmpty() }

    loaded shouldBe mapOf(london.id to snapshot)
  }

  @Test
  fun forceRefreshShowsANewlyPublishedRepertoire() = test {
    val viewModel =
      buildViewModel(
        loadManifest = { CachedManifestResult.Fresh(manifest(london)) },
        reloadManifest = { CachedManifestResult.Fresh(manifest(london, caroKann)) },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.forceRefresh()

    val loaded =
      viewModel.catalogState.first { it is LibraryCatalogState.Loaded && it.repertoires.size == 2 }
        as LibraryCatalogState.Loaded
    loaded.repertoires shouldBe listOf(london, caroKann)
  }

  @Test
  fun forceRefreshAlsoReloadsMyRepertoires() = test {
    var currentIds = listOf("italian-game")
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest()) },
        loadMyRepertoires = {
          currentIds.map { DataRepertoire(id = it, name = it, color = RepertoireColor.WHITE) }
        },
      )
    viewModel.myRepertoires.first { it.isNotEmpty() }
    currentIds = listOf("italian-game", "italian-game-copy")

    viewModel.forceRefresh()
    val loaded = viewModel.myRepertoires.first { it.size == 2 }

    loaded.map { it.id } shouldBe listOf("italian-game", "italian-game-copy")
  }

  @Test
  fun forceRefreshIsIgnoredWhileALoadIsInFlight() = test {
    val gate = CompletableDeferred<Unit>()
    var loadCalls = 0
    val viewModel =
      buildViewModel({
        loadCalls++
        gate.await()
        CachedManifestResult.Fresh(manifest(london))
      })
    testScheduler.advanceUntilIdle()

    viewModel.forceRefresh()
    viewModel.forceRefresh()
    gate.complete(Unit)
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    loadCalls shouldBe 1
  }

  @Test
  fun refreshWhileLoadIsInFlightIsIgnored() = test {
    val gate = CompletableDeferred<Unit>()
    var loadCalls = 0
    val viewModel =
      buildViewModel({
        loadCalls++
        gate.await()
        CachedManifestResult.Fresh(manifest(london))
      })
    testScheduler.advanceUntilIdle()

    viewModel.refresh()
    viewModel.refresh()
    gate.complete(Unit)
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    loadCalls shouldBe 1
  }

  @Test
  fun persistedInstalledIdsAreReflectedOnLoad() = test {
    val store = FakeRegistry()
    store.markInstalled(london.id)
    val viewModel =
      buildViewModel({ CachedManifestResult.Fresh(manifest(london, caroKann)) }, store = store)

    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.installStates.value[london.id] shouldBe
      RepertoireInstallState.Installed(summary = null)
    viewModel.installStates.value[caroKann.id] shouldBe RepertoireInstallState.NotInstalled
  }

  @Test
  fun anOwnRepertoireWithTheCatalogIdIsNotInstalled() = test {
    val store = FakeRegistry()
    store.markOwn(london.id)
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest(london)) }, store = store)

    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.installStates.value[london.id] shouldBe RepertoireInstallState.NotInstalled
  }

  @Test
  fun installIsRefusedWhenAnOwnRepertoireUsesTheId() = test {
    val store = FakeRegistry()
    store.markOwn(london.id)
    var imported = false
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importMoves = { _, _, _ ->
          imported = true
          PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 0)
        },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    state[london.id] shouldBe RepertoireInstallState.Failed(InstallError.IdTaken)
    imported shouldBe false
    store.isInstalled(london.id) shouldBe false
  }

  @Test
  fun installRegistersTheDescriptorVersion() = test {
    val store = FakeRegistry()
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest(london)) }, store = store)
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    store.rows.getValue(london.id).installedVersion shouldBe london.version
  }

  @Test
  fun installSuccessMarksInstalledAndReportsSummary() = test {
    val store = FakeRegistry()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importMoves = { _, _, _ -> PgnImportSummary(movesAdded = 5, movesAlreadyPresent = 2) },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    state[london.id] shouldBe
      RepertoireInstallState.Installed(PgnImportSummary(movesAdded = 5, movesAlreadyPresent = 2))
    store.isInstalled(london.id) shouldBe true
  }

  @Test
  fun installForwardsTheRepertoireColorToTheImporter() = test {
    var importedColor: RepertoireColor? = null
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(caroKann)) },
        importMoves = { descriptor, _, _ ->
          importedColor = descriptor.color
          PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 0)
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(caroKann)
    viewModel.installStates.first { it[caroKann.id] is RepertoireInstallState.Installed }

    importedColor shouldBe RepertoireColor.BLACK
  }

  @Test
  fun fetchNetworkErrorFailsTheInstallWithoutMarking() = test {
    val store = FakeRegistry()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.NetworkError("offline") },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    state[london.id] shouldBe RepertoireInstallState.Failed(InstallError.Network("offline"))
    store.isInstalled(london.id) shouldBe false
  }

  @Test
  fun fetchHttpErrorFailsTheInstallWithoutMarking() = test {
    val store = FakeRegistry()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.HttpError(500) },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    state[london.id] shouldBe RepertoireInstallState.Failed(InstallError.Http(500))
    store.isInstalled(london.id) shouldBe false
  }

  @Test
  fun malformedPgnFailsTheInstallWithoutMarking() = test {
    val store = FakeRegistry()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.MalformedRepertoire("no moves") },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    state[london.id] shouldBe
      RepertoireInstallState.Failed(InstallError.MalformedRepertoire("no moves"))
    store.isInstalled(london.id) shouldBe false
  }

  @Test
  fun importFailureAfterSuccessfulFetchDoesNotMarkInstalled() = test {
    val store = FakeRegistry()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importMoves = { _, _, _ ->
          throw PgnImportException("Illegal move Qx9 at ply 4 in game 1")
        },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    state[london.id] shouldBe
      RepertoireInstallState.Failed(
        InstallError.ImportFailed("Illegal move Qx9 at ply 4 in game 1")
      )
    store.isInstalled(london.id) shouldBe false
  }

  @Test
  fun doubleInstallRequestRunsASingleInstall() = test {
    val gate = CompletableDeferred<Unit>()
    var fetchCalls = 0
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ ->
          fetchCalls++
          gate.await()
          CatalogResult.Ok(emptyList())
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    viewModel.install(london)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    fetchCalls shouldBe 1
  }

  @Test
  fun installOnAnAlreadyInstalledRepertoireReinstalls() = test {
    val store = FakeRegistry()
    store.markInstalled(london.id)
    var fetchCalls = 0
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ ->
          fetchCalls++
          CatalogResult.Ok(emptyList())
        },
        importMoves = { _, _, _ -> PgnImportSummary(movesAdded = 2, movesAlreadyPresent = 6) },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
    // The catalog seeds an Installed(summary = null) state from persistence.
    viewModel.installStates.value[london.id] shouldBe
      RepertoireInstallState.Installed(summary = null)

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    // Reinstall re-fetches and re-imports, restoring removed moves and reporting the fresh summary.
    fetchCalls shouldBe 1
    state[london.id] shouldBe
      RepertoireInstallState.Installed(PgnImportSummary(movesAdded = 2, movesAlreadyPresent = 6))
    store.isInstalled(london.id) shouldBe true
  }

  @Test
  fun firstInstallReportsItToTheServer() = test {
    val reportedIds = mutableListOf<String>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        reportInstall = { reportedIds.add(it) },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }
    // Installed is published before reportInstall is even called (it must never gate on the
    // report), so a fake that itself suspends needs the scheduler drained before asserting.
    testScheduler.advanceUntilIdle()

    reportedIds shouldBe listOf(london.id)
  }

  @Test
  fun installIsShownAsCompleteWithoutWaitingForReportInstallToReturn() = test {
    val gate = CompletableDeferred<Unit>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        reportInstall = { gate.await() },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)

    // Installed must be observable while the (never completing here) report call is still in
    // flight, proving the install outcome is not gated on that best-effort telemetry round trip.
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }
    gate.complete(Unit)
  }

  @Test
  fun reinstallOfAnAlreadyInstalledRepertoireDoesNotReportAgain() = test {
    val store = FakeRegistry()
    store.markInstalled(london.id)
    val reportedIds = mutableListOf<String>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        reportInstall = { reportedIds.add(it) },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    reportedIds shouldBe emptyList()
  }

  @Test
  fun aFailedInstallNeverReportsIt() = test {
    val reportedIds = mutableListOf<String>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.HttpError(500) },
        reportInstall = { reportedIds.add(it) },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    reportedIds shouldBe emptyList()
  }

  @Test
  fun installStartsAtZeroFetchProgress() = test {
    val gate = CompletableDeferred<Unit>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ ->
          gate.await()
          CatalogResult.Ok(emptyList())
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)

    // The guard sets Fetching(0f) synchronously, before the fetch coroutine is even launched.
    viewModel.installStates.value[london.id] shouldBe RepertoireInstallState.Fetching(0f)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }
  }

  @Test
  fun installReportsFetchProgress() = test {
    val gate = CompletableDeferred<Unit>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, onProgress ->
          onProgress(0.5f)
          gate.await()
          CatalogResult.Ok(emptyList())
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val midFetch =
      viewModel.installStates.first { it[london.id] == RepertoireInstallState.Fetching(0.5f) }

    midFetch[london.id] shouldBe RepertoireInstallState.Fetching(0.5f)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }
  }

  @Test
  fun installStartsImportAtZeroProgress() = test {
    val gate = CompletableDeferred<Unit>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importMoves = { _, _, _ ->
          gate.await()
          PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 0)
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val midImport =
      viewModel.installStates.first { it[london.id] == RepertoireInstallState.Importing(0f) }

    midImport[london.id] shouldBe RepertoireInstallState.Importing(0f)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }
  }

  @Test
  fun installReportsImportProgress() = test {
    val gate = CompletableDeferred<Unit>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importMoves = { _, _, onProgress ->
          onProgress(0.5f)
          gate.await()
          PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 0)
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val midImport =
      viewModel.installStates.first { it[london.id] == RepertoireInstallState.Importing(0.5f) }

    midImport[london.id] shouldBe RepertoireInstallState.Importing(0.5f)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }
  }

  @Test
  fun requestPreviewPublishesTheOverlap() = test {
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(caroKann)) },
        fetchMoves = { _, _ -> CatalogResult.Ok(emptyList()) },
        previewMoves = { _, _ -> PgnImportPreview(totalMoves = 12, movesInCommon = 5) },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.requestPreview(caroKann)
    val state = viewModel.previewStates.first { it[caroKann.id] is RepertoirePreviewState.Ready }

    state[caroKann.id] shouldBe
      RepertoirePreviewState.Ready(PgnImportPreview(totalMoves = 12, movesInCommon = 5))
  }

  @Test
  fun requestPreviewForwardsTheDownloadedMoves() = test {
    val downloaded = listOf(RepertoireMove(from = "a", move = "e4", to = "b", isGood = true))
    var previewed: List<RepertoireMove>? = null
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(caroKann)) },
        fetchMoves = { _, _ -> CatalogResult.Ok(downloaded) },
        previewMoves = { _, moves ->
          previewed = moves
          PgnImportPreview(totalMoves = 1, movesInCommon = 0)
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.requestPreview(caroKann)
    viewModel.previewStates.first { it[caroKann.id] is RepertoirePreviewState.Ready }

    previewed shouldBe downloaded
  }

  @Test
  fun installFetchesTheRequestedDescriptor() = test {
    var fetched: RepertoireDescriptor? = null
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(caroKann)) },
        fetchMoves = { descriptor, _ ->
          fetched = descriptor
          CatalogResult.Ok(emptyList())
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(caroKann)
    viewModel.installStates.first { it[caroKann.id] is RepertoireInstallState.Installed }

    fetched shouldBe caroKann
  }

  @Test
  fun requestPreviewSurfacesAFetchFailure() = test {
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.HttpError(404) },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.requestPreview(london)
    val state = viewModel.previewStates.first { it[london.id] is RepertoirePreviewState.Failed }

    state[london.id] shouldBe RepertoirePreviewState.Failed(InstallError.Http(404))
  }

  @Test
  fun requestPreviewWhileOneIsLoadingIsIgnored() = test {
    val gate = CompletableDeferred<Unit>()
    var fetchCalls = 0
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ ->
          fetchCalls++
          gate.await()
          CatalogResult.Ok(emptyList())
        },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.requestPreview(london)
    viewModel.requestPreview(london)
    gate.complete(Unit)
    viewModel.previewStates.first { it[london.id] is RepertoirePreviewState.Ready }

    fetchCalls shouldBe 1
  }

  @Test
  fun requestPreviewSurfacesAComputationFailure() = test {
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.Ok(emptyList()) },
        previewMoves = { _, _ -> throw PgnImportException("Illegal move Qx9 at ply 4 in game 1") },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.requestPreview(london)
    val state = viewModel.previewStates.first { it[london.id] is RepertoirePreviewState.Failed }

    state[london.id] shouldBe
      RepertoirePreviewState.Failed(
        InstallError.ImportFailed("Illegal move Qx9 at ply 4 in game 1")
      )
  }

  @Test
  fun requestPreviewMapsAMalformedManifestToMalformedRepertoire() = test {
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ -> CatalogResult.MalformedManifest("bad") },
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.requestPreview(london)
    val state = viewModel.previewStates.first { it[london.id] is RepertoirePreviewState.Failed }

    state[london.id] shouldBe RepertoirePreviewState.Failed(InstallError.MalformedRepertoire("bad"))
  }

  @Test
  fun failedInstallCanBeRetriedToSuccess() = test {
    val store = FakeRegistry()
    var failNext = true
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ ->
          if (failNext) {
            failNext = false
            CatalogResult.HttpError(503)
          } else {
            CatalogResult.Ok(emptyList())
          }
        },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    state[london.id].shouldBeInstanceOf<RepertoireInstallState.Installed>()
    store.isInstalled(london.id) shouldBe true
  }

  @Test
  fun refreshPreservesSessionInstallSummaries() = test {
    val store = FakeRegistry()
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest(london)) }, store = store)
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    viewModel.refresh()
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.installStates.value[london.id] shouldBe
      RepertoireInstallState.Installed(PgnImportSummary(movesAdded = 3, movesAlreadyPresent = 1))
  }

  private suspend fun RepertoireLibraryViewModel.loadedState() = catalogState.first {
    it is LibraryCatalogState.Loaded
  }

  @Test
  fun installIsIgnoredWhileAnUninstallIsInFlight() = test {
    val gate = CompletableDeferred<Unit>()
    var fetches = 0
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchMoves = { _, _ ->
          fetches++
          CatalogResult.Ok(emptyList())
        },
        store = store,
        uninstallRepertoire = {
          gate.await()
          store.rows.remove(it)
        },
      )
    viewModel.loadedState()
    viewModel.uninstall(london.id)

    viewModel.install(london)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] == RepertoireInstallState.NotInstalled }

    fetches shouldBe 0
  }

  @Test
  fun anInstalledRepertoireMissingFromTheCatalogCanBeUninstalled() = test {
    val done = CompletableDeferred<String>()
    val store = FakeRegistry().apply { markInstalled(caroKann.id) }
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        store = store,
        uninstallRepertoire = { done.complete(it) },
      )
    viewModel.loadedState()

    viewModel.uninstall(caroKann.id)

    done.await() shouldBe caroKann.id
  }

  @Test
  fun uninstallMarksTheRepertoireNotInstalled() = test {
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest(london)) }, store = store)
    viewModel.loadedState()

    viewModel.uninstall(london.id)
    viewModel.installStates.first { it[london.id] == RepertoireInstallState.NotInstalled }

    store.rows[london.id] shouldBe null
  }

  @Test
  fun uninstallIsIgnoredWhileAnInstallIsInFlight() = test {
    val gate = CompletableDeferred<Unit>()
    val uninstalled = mutableListOf<String>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importMoves = { _, _, _ ->
          gate.await()
          PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 0)
        },
        uninstallRepertoire = { uninstalled += it },
      )
    viewModel.loadedState()
    viewModel.install(london)

    viewModel.uninstall(london.id)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    uninstalled shouldBe emptyList()
  }

  @Test
  fun aSecondUninstallTapIsIgnored() = test {
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        store = store,
        uninstallRepertoire = {
          calls++
          gate.await()
        },
      )
    viewModel.loadedState()

    viewModel.uninstall(london.id)
    viewModel.uninstall(london.id)
    gate.complete(Unit)
    viewModel.installStates.first { it[london.id] == RepertoireInstallState.NotInstalled }

    calls shouldBe 1
  }

  @Test
  fun aFailedUninstallKeepsTheRepertoireInstalled() = test {
    val attempted = CompletableDeferred<Unit>()
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        store = store,
        uninstallRepertoire = {
          attempted.complete(Unit)
          throw IllegalStateException("disk full")
        },
      )
    viewModel.loadedState()

    viewModel.uninstall(london.id)
    attempted.await()

    viewModel.installStates.value[london.id].shouldBeInstanceOf<RepertoireInstallState.Installed>()
  }

  @Test
  fun aCancelledUninstallKeepsTheRepertoireInstalled() = test {
    val attempted = CompletableDeferred<Unit>()
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        store = store,
        uninstallRepertoire = {
          attempted.complete(Unit)
          throw CancellationException("left the page")
        },
      )
    viewModel.loadedState()

    viewModel.uninstall(london.id)
    attempted.await()

    viewModel.installStates.value[london.id].shouldBeInstanceOf<RepertoireInstallState.Installed>()
  }

  @Test
  fun requestUninstallPreviewPublishesTheCount() = test {
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        store = store,
        previewUninstall = { 3 },
      )
    viewModel.loadedState()

    viewModel.requestUninstallPreview(london.id)

    viewModel.uninstallPreviews.first { london.id in it }[london.id] shouldBe 3
  }

  @Test
  fun anInstallBehindTheCatalogVersionIsOutdated() = test {
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london.copy(version = 2))) },
        store = store,
      )
    viewModel.loadedState()

    viewModel.installStates.value[london.id] shouldBe
      RepertoireInstallState.Installed(summary = null, isOutdated = true)
  }

  @Test
  fun installingTheCatalogVersionClearsOutdated() = test {
    val store = FakeRegistry().apply { markInstalled(london.id) }
    val latest = london.copy(version = 2)
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest(latest)) }, store = store)
    viewModel.loadedState()

    viewModel.install(latest)
    val states =
      viewModel.installStates.first {
        (it[london.id] as? RepertoireInstallState.Installed)?.summary != null
      }

    (states[london.id] as RepertoireInstallState.Installed).isOutdated shouldBe false
  }

  @Test
  fun aReinstallAfterAnUninstallIsReportedAgain() = test {
    val reported = mutableListOf<String>()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        reportInstall = { reported += it },
      )
    viewModel.loadedState()
    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }
    viewModel.uninstall(london.id)
    viewModel.installStates.first { it[london.id] == RepertoireInstallState.NotInstalled }

    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    reported shouldBe listOf(london.id, london.id)
  }
}

/** In memory stand in for the synced repertoire registry. */
private class FakeRegistry {
  val rows = mutableMapOf<String, DataRepertoire>()

  fun markInstalled(id: String) {
    rows[id] = DataRepertoire(id, id, color = null, installedVersion = 1)
  }

  fun markOwn(id: String) {
    rows[id] = DataRepertoire(id, id, color = null)
  }

  fun isInstalled(id: String): Boolean = rows[id]?.installedVersion != null
}
