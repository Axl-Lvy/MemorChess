package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import proj.memorchess.axl.core.config.INSTALLED_REPERTOIRES_SETTING
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.test_util.TestWithKoin

class TestRepertoireLibraryViewModel : TestWithKoin() {

  override suspend fun setUp() {
    INSTALLED_REPERTOIRES_SETTING.reset()
  }

  private val london =
    RepertoireDescriptor(
      id = "london-system-white",
      name = "London System",
      color = RepertoireColor.WHITE,
      description = "Solid.",
      moveCount = 73,
      file = "pgn/london-system-white.pgn",
    )

  private val caroKann =
    RepertoireDescriptor(
      id = "caro-kann-black",
      name = "Caro Kann",
      color = RepertoireColor.BLACK,
      description = "Sturdy.",
      moveCount = 0,
      file = "pgn/caro-kann-black.pgn",
    )

  private fun manifest(vararg descriptors: RepertoireDescriptor) =
    RepertoireManifest(schemaVersion = 1, repertoires = descriptors.toList())

  private fun TestScope.buildViewModel(
    loadManifest: suspend () -> CachedManifestResult,
    fetchPgn: suspend (String) -> CatalogResult<List<PgnGame>> = { CatalogResult.Ok(emptyList()) },
    importGames: suspend (List<PgnGame>) -> PgnImportSummary = {
      PgnImportSummary(movesAdded = 3, movesAlreadyPresent = 1)
    },
    store: InstalledRepertoireStore = InstalledRepertoireStore(),
  ) =
    RepertoireLibraryViewModel(
      loadManifest = loadManifest,
      fetchPgn = fetchPgn,
      importGames = importGames,
      installedStore = store,
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
    val store = InstalledRepertoireStore()
    store.markInstalled(london.id)
    val viewModel =
      buildViewModel({ CachedManifestResult.Fresh(manifest(london, caroKann)) }, store = store)

    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.installStates.value[london.id] shouldBe
      RepertoireInstallState.Installed(summary = null)
    viewModel.installStates.value[caroKann.id] shouldBe RepertoireInstallState.NotInstalled
  }

  @Test
  fun installSuccessMarksInstalledAndReportsSummary() = test {
    val store = InstalledRepertoireStore()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importGames = { PgnImportSummary(movesAdded = 5, movesAlreadyPresent = 2) },
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
  fun fetchNetworkErrorFailsTheInstallWithoutMarking() = test {
    val store = InstalledRepertoireStore()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchPgn = { CatalogResult.NetworkError("offline") },
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
    val store = InstalledRepertoireStore()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchPgn = { CatalogResult.HttpError(500) },
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
    val store = InstalledRepertoireStore()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchPgn = { CatalogResult.MalformedPgn("no moves") },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    val state = viewModel.installStates.first { it[london.id] is RepertoireInstallState.Failed }

    state[london.id] shouldBe RepertoireInstallState.Failed(InstallError.MalformedPgn("no moves"))
    store.isInstalled(london.id) shouldBe false
  }

  @Test
  fun importFailureAfterSuccessfulFetchDoesNotMarkInstalled() = test {
    val store = InstalledRepertoireStore()
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        importGames = { throw PgnImportException("Illegal move Qx9 at ply 4 in game 1") },
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
        fetchPgn = {
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
  fun installOnAnAlreadyInstalledRepertoireIsIgnored() = test {
    val store = InstalledRepertoireStore()
    store.markInstalled(london.id)
    var fetchCalls = 0
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchPgn = {
          fetchCalls++
          CatalogResult.Ok(emptyList())
        },
        store = store,
      )
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.install(london)
    testScheduler.advanceUntilIdle()

    fetchCalls shouldBe 0
    viewModel.installStates.value[london.id] shouldBe
      RepertoireInstallState.Installed(summary = null)
  }

  @Test
  fun failedInstallCanBeRetriedToSuccess() = test {
    val store = InstalledRepertoireStore()
    var failNext = true
    val viewModel =
      buildViewModel(
        { CachedManifestResult.Fresh(manifest(london)) },
        fetchPgn = {
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
    val store = InstalledRepertoireStore()
    val viewModel = buildViewModel({ CachedManifestResult.Fresh(manifest(london)) }, store = store)
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }
    viewModel.install(london)
    viewModel.installStates.first { it[london.id] is RepertoireInstallState.Installed }

    viewModel.refresh()
    viewModel.catalogState.first { it is LibraryCatalogState.Loaded }

    viewModel.installStates.value[london.id] shouldBe
      RepertoireInstallState.Installed(PgnImportSummary(movesAdded = 3, movesAlreadyPresent = 1))
  }
}
