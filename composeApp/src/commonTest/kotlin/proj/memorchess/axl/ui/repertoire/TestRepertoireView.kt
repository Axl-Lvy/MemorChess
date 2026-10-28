package proj.memorchess.axl.ui.repertoire

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.RepertoireMasterySnapshot
import proj.memorchess.axl.core.data.repertoire.CachedRepertoireCatalog
import proj.memorchess.axl.core.data.repertoire.InstallError
import proj.memorchess.axl.core.data.repertoire.LibraryCatalogState
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.data.repertoire.RepertoireInstallState
import proj.memorchess.axl.core.data.repertoire.RepertoirePreviewState
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImportPreview
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.repertoireMovesJsonOf
import proj.memorchess.axl.ui.assertTileContainsPiece
import proj.memorchess.axl.ui.assertTileIsEmpty
import proj.memorchess.axl.ui.pages.RepertoireLibraryActions
import proj.memorchess.axl.ui.pages.RepertoireLibraryContent
import proj.memorchess.axl.ui.pages.RepertoireView
import proj.memorchess.axl.ui.playMove
import proj.memorchess.axl.ui.setTestContent
import proj.memorchess.axl.ui.waitUntilBoardAppears
import proj.memorchess.axl.ui.waitUntilNodeExists
import proj.memorchess.axl.ui.waitUntilSuspending

@OptIn(ExperimentalTestApi::class)
class TestRepertoireView : TestWithKoin() {

  private val whitePawn = ChessPiece(PieceKind.PAWN, Player.WHITE)
  private val treeStore: TreeStore by inject()

  private val descriptor =
    RepertoireDescriptor(
      id = "test-rep",
      name = "Test Repertoire",
      color = RepertoireColor.WHITE,
      description = "A tiny repertoire.",
      moveCount = 3,
      version = 1,
    )

  /**
   * A second WHITE fixture, used whenever a test needs to tell "the hero" apart from "the card".
   */
  private val secondDescriptor =
    RepertoireDescriptor(
      id = "test-rep-2",
      name = "Second Repertoire",
      color = RepertoireColor.WHITE,
      description = "Another tiny repertoire.",
      moveCount = 5,
      version = 1,
    )

  /** A BLACK fixture, used to exercise the color filter chips against a mixed catalog. */
  private val blackDescriptor =
    RepertoireDescriptor(
      id = "test-rep-black",
      name = "Black Repertoire",
      color = RepertoireColor.BLACK,
      description = "A black repertoire.",
      moveCount = 4,
      version = 1,
    )

  private fun cardTag(d: RepertoireDescriptor) = "library_repertoire_card:${d.id}"

  private fun progressTag(d: RepertoireDescriptor) = "library_progress:${d.id}"

  /** Reads the live [ProgressBarRangeInfo.current] off the node tagged [tag], mid-animation. */
  private fun ComposeUiTest.currentProgress(tag: String): Float =
    onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

  /** Catalog + client backed by a [MockEngine] serving a one line manifest and its moves. */
  private fun mockCatalog(
    pgn: String = "1. e4 e5 2. Nf3 *"
  ): Pair<CachedRepertoireCatalog, RepertoireCatalogClient> {
    val manifest =
      """{"schemaVersion":2,"repertoires":[{"id":"test-rep","name":"Test Repertoire",""" +
        """"color":"white","description":"A tiny repertoire.","moveCount":3,""" +
        """"version":1}]}"""
    val engine = MockEngine { request ->
      val path = request.url.encodedPath
      when {
        path.endsWith("manifest.json") -> respond(manifest, HttpStatusCode.OK)
        path.endsWith("test-rep/versions/1/moves") ->
          respond(repertoireMovesJsonOf(pgn), HttpStatusCode.OK)
        else -> respond("", HttpStatusCode.NotFound)
      }
    }
    val client = RepertoireCatalogClient(HttpClient(engine), baseUrl = "https://example.test")
    return CachedRepertoireCatalog(client) to client
  }

  private fun runViewer(
    pgn: String = "1. e4 e5 2. Nf3 *",
    seedTreeStore: suspend (TreeStore) -> Unit = {},
    block: suspend ComposeUiTest.() -> Unit,
  ) = runComposeUiTest {
    koinSetUp()
    try {
      val (catalog, client) = mockCatalog(pgn)
      seedTreeStore(treeStore)
      setTestContent { InitializeApp { RepertoireView("test-rep", catalog, client) } }
      // Wait for the async PGN load to render the board before asserting (issue #228).
      waitUntilBoardAppears()
      block()
    } finally {
      koinTearDown()
    }
  }

  /** Runs a [RepertoireLibraryContent] test with Koin set up and torn down around [block]. */
  private fun runLibraryTest(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  /** Renders [RepertoireLibraryContent] with the given state, recording actions by default. */
  private suspend fun ComposeUiTest.setLibraryContent(
    catalogState: LibraryCatalogState,
    installStates: Map<String, RepertoireInstallState> = emptyMap(),
    previewStates: Map<String, RepertoirePreviewState> = emptyMap(),
    masterySnapshots: Map<String, RepertoireMasterySnapshot> = emptyMap(),
    onInstall: (RepertoireDescriptor) -> Unit = {},
    onPreviewRequest: (RepertoireDescriptor) -> Unit = {},
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onView: (RepertoireDescriptor) -> Unit = {},
    uninstallPreviews: Map<String, Int> = emptyMap(),
    onUninstallRequest: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},
    myRepertoires: List<DataRepertoire> = emptyList(),
  ) {
    setTestContent {
      InitializeApp {
        RepertoireLibraryContent(
          catalogState = catalogState,
          installStates = installStates,
          previewStates = previewStates,
          myRepertoires = myRepertoires,
          masterySnapshots = masterySnapshots,
          installedIds = myRepertoires.filter { it.installedVersion != null }.map { it.id }.toSet(),
          uninstallPreviews = uninstallPreviews,
          actions =
            RepertoireLibraryActions(
              onInstall = onInstall,
              onPreviewRequest = onPreviewRequest,
              onRetry = onRetry,
              onRefresh = onRefresh,
              onView = onView,
              onUninstallRequest = onUninstallRequest,
              onUninstall = onUninstall,
            ),
        )
      }
    }
  }

  @Test
  fun bookMoveCanBePlayed() =
    runViewer(
      seedTreeStore = { store ->
        val afterE4 = GameEngine().apply { playSanMove("e4") }.toPositionKey()
        val afterE4E5 =
          GameEngine()
            .apply {
              playSanMove("e4")
              playSanMove("e5")
            }
            .toPositionKey()
        store.addMove(
          from = PositionKey.START_POSITION,
          move = "e4",
          to = afterE4,
          isGood = true,
          fromDepth = 0,
        )
        store.addMove(from = afterE4, move = "e5", to = afterE4E5, isGood = true, fromDepth = 1)
      }
    ) {
      onNodeWithText("READ ONLY").assertExists()
      waitUntilNodeExists(hasTestTag("Play e4").and(hasText("YOURS"))).assertExists()
      assertTileContainsPiece("e2", whitePawn)
      playMove("e2", "e4")
      assertTileContainsPiece("e4", whitePawn)
      assertTileIsEmpty("e2")
      waitUntilNodeExists(hasTestTag("Play e5").and(hasText("YOURS"))).assertExists()
    }

  @Test
  fun yoursTagOnlyMarksClassifiedGoodMoves() =
    runViewer(
      pgn = "1. e4 (1. d4) 1... e5 *",
      seedTreeStore = { store ->
        val afterE4 = GameEngine().apply { playSanMove("e4") }.toPositionKey()
        val afterD4 = GameEngine().apply { playSanMove("d4") }.toPositionKey()
        store.addMove(
          from = PositionKey.START_POSITION,
          move = "e4",
          to = afterE4,
          isGood = true,
          fromDepth = 0,
        )
        store.addMove(
          from = PositionKey.START_POSITION,
          move = "d4",
          to = afterD4,
          isGood = false,
          fromDepth = 0,
        )
      },
    ) {
      waitUntilNodeExists(hasTestTag("Play e4").and(hasText("YOURS"))).assertExists()
      onNode(hasTestTag("Play d4")).assertExists()
      onNode(hasTestTag("Play d4").and(hasText("YOURS"))).assertDoesNotExist()
    }

  @Test
  fun offBookMoveIsRejected() = runViewer {
    onNodeWithText("READ ONLY").assertExists()
    assertTileContainsPiece("e2", whitePawn)
    // d2-d4 is legal but not in the repertoire whose only first move is e4.
    playMove("d2", "d4")
    assertTileContainsPiece("d2", whitePawn)
    assertTileIsEmpty("d4")
  }

  @Test
  fun viewerHasNoSaveOrDeleteControls() = runViewer {
    onNodeWithText("READ ONLY").assertExists()
    assertTileContainsPiece("e2", whitePawn)
    onNodeWithContentDescription("Save").assertDoesNotExist()
    onNodeWithContentDescription("Delete").assertDoesNotExist()
  }

  @Test
  fun cardViewButtonTriggersOnView() = runComposeUiTest {
    koinSetUp()
    try {
      var viewed: RepertoireDescriptor? = null
      setTestContent {
        InitializeApp {
          RepertoireLibraryContent(
            catalogState = LibraryCatalogState.Loaded(listOf(descriptor), isStale = false),
            installStates = emptyMap<String, RepertoireInstallState>(),
            previewStates = emptyMap<String, RepertoirePreviewState>(),
            myRepertoires = emptyList(),
            actions =
              RepertoireLibraryActions(
                onInstall = {},
                onPreviewRequest = {},
                onRetry = {},
                onView = { viewed = it },
              ),
          )
        }
      }
      onNodeWithTag("library_repertoire_card:test-rep:view").performClick()
      assertEquals(descriptor, viewed)
    } finally {
      koinTearDown()
    }
  }

  // ---------------------------------------------------------------------------------------------
  // LibraryCatalogState coverage
  // ---------------------------------------------------------------------------------------------

  @Test
  fun loadingStateShowsSkeletons() = runLibraryTest {
    setLibraryContent(catalogState = LibraryCatalogState.Loading)
    onAllNodesWithTag("library_catalog_skeleton").assertCountEquals(3)
    onNodeWithTag(cardTag(descriptor)).assertDoesNotExist()
  }

  @Test
  fun networkErrorShowsRetry() = runLibraryTest {
    var retried = false
    setLibraryContent(
      catalogState = LibraryCatalogState.NetworkError("boom"),
      onRetry = { retried = true },
    )
    onNodeWithText("RETRY").performClick()
    assertTrue(retried)
  }

  @Test
  fun refreshButtonTriggersOnRefresh() = runLibraryTest {
    var refreshed = false
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(emptyList(), isStale = false),
      onRefresh = { refreshed = true },
    )
    onNodeWithTag("library_refresh_button").performClick()
    assertTrue(refreshed)
  }

  @Test
  fun httpErrorShowsRetry() = runLibraryTest {
    var retried = false
    setLibraryContent(
      catalogState = LibraryCatalogState.HttpError(503),
      onRetry = { retried = true },
    )
    onNodeWithText("RETRY").performClick()
    assertTrue(retried)
  }

  @Test
  fun malformedManifestShowsRetry() = runLibraryTest {
    var retried = false
    setLibraryContent(
      catalogState = LibraryCatalogState.MalformedManifest("bad json"),
      onRetry = { retried = true },
    )
    onNodeWithText("RETRY").performClick()
    assertTrue(retried)
  }

  @Test
  fun loadedEmptyShowsEmptyMessage() = runLibraryTest {
    setLibraryContent(catalogState = LibraryCatalogState.Loaded(emptyList(), isStale = false))
    onNodeWithText("The catalog is empty for now. Check back later.").assertExists()
    onNodeWithTag("library_hero_card").assertDoesNotExist()
    onNodeWithTag("library_filter_chip:all").assertDoesNotExist()
  }

  @Test
  fun loadedStaleShowsStaleHint() = runLibraryTest {
    setLibraryContent(catalogState = LibraryCatalogState.Loaded(listOf(descriptor), isStale = true))
    onNodeWithText("Showing a previously downloaded catalog. It may be out of date.").assertExists()
    onNodeWithTag("library_hero_card").assertExists()
    onNodeWithTag(cardTag(descriptor)).assertExists()
  }

  // ---------------------------------------------------------------------------------------------
  // RepertoireInstallState coverage. Every case here uses a two entry catalog and only ever puts
  // `secondDescriptor` into an install state, leaving `descriptor` not installed; with both
  // carrying the same (default 0) downloadCount, pickHeroRepertoire's tie-break lands on
  // `descriptor` as the hero, so it never collides on name or tag with the card under test
  // (`secondDescriptor`).
  // ---------------------------------------------------------------------------------------------

  @Test
  fun notInstalledShowsInstallButtonAndNewBadge() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = emptyMap(),
    )
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNode(hasText("NEW").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor))))).assertExists()
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun authoredShowsYoursBadgeWithoutInstallOrUninstall() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Authored),
    )
    onNode(hasText("YOURS").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertDoesNotExist()
    onNodeWithTag("${cardTag(secondDescriptor)}:uninstall").assertDoesNotExist()
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun fetchingAtFullByteProgressShowsThirtyPercent() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching(1f)),
    )
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))
    onNode(hasText("Downloading…").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
  }

  @Test
  fun fetchingAtHalfByteProgressShowsFifteenPercent() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching(0.5f)),
    )
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.15f, 0f..1f))
  }

  @Test
  fun fetchingAtZeroByteProgressShowsZeroPercent() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching(0f)),
    )
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
  }

  @Test
  fun importingAtFullPlanningProgressShowsHundredPercent() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Importing(1f)),
    )
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(1.00f, 0f..1f))
    onNode(hasText("Importing…").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
  }

  @Test
  fun importingAtZeroPlanningProgressShowsThirtyPercent() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Importing(0f)),
    )
    // Importing(0f) hands off exactly where Fetching(1f) left the bar (30%): the game-planning
    // loop has not reported its first game yet, but the fetch phase it follows is done.
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))
  }

  @Test
  fun installedWithSummaryShowsSummaryAndInTrainingBadge() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(
          secondDescriptor.id to
            RepertoireInstallState.Installed(
              summary = PgnImportSummary(movesAdded = 5, movesAlreadyPresent = 2)
            )
        ),
      masterySnapshots = mapOf(secondDescriptor.id to snapshot(solid = 3, total = 10)),
    )
    onNodeWithText("Moves added: 5 · Already present: 2").assertExists()
    onNode(hasText("IN TRAINING").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))
  }

  @Test
  fun anInstalledCardOffersUninstall() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Installed(summary = null)),
    )
    onNodeWithTag("${cardTag(secondDescriptor)}:uninstall").assertExists()
    onNodeWithTag("${cardTag(descriptor)}:uninstall").assertDoesNotExist()
  }

  @Test
  fun uninstallAsksForConfirmationWithTheCount() = runLibraryTest {
    val requested = mutableListOf<String>()
    val uninstalled = mutableListOf<String>()
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Installed(summary = null)),
      uninstallPreviews = mapOf(secondDescriptor.id to 3),
      onUninstallRequest = { requested += it },
      onUninstall = { uninstalled += it },
    )

    onNodeWithTag("${cardTag(secondDescriptor)}:uninstall").performClick()

    waitUntilNodeExists(hasText("3 moves and their review progress will be deleted."))
    assertEquals(listOf(secondDescriptor.id), requested)
    assertEquals(emptyList<String>(), uninstalled)
    onNodeWithTag("confirmDialogOkButton").performClick()
    assertEquals(listOf(secondDescriptor.id), uninstalled)
  }

  @Test
  fun anInstalledRepertoireInMyRepertoiresOffersUninstall() = runLibraryTest {
    val uninstalled = mutableListOf<String>()
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(listOf(descriptor), isStale = false),
      myRepertoires =
        listOf(
          DataRepertoire("gone", "Gone", color = null, installedVersion = 1),
          DataRepertoire("mine", "Mine", color = RepertoireColor.WHITE),
        ),
      uninstallPreviews = mapOf("gone" to 2),
      onUninstall = { uninstalled += it },
    )
    onNodeWithTag("library_my_repertoire_row:mine:uninstall").assertDoesNotExist()

    onNodeWithTag("library_my_repertoire_row:gone:uninstall").performClick()
    waitUntilNodeExists(hasText("2 moves and their review progress will be deleted."))
    onNodeWithTag("confirmDialogOkButton").performClick()

    assertEquals(listOf("gone"), uninstalled)
  }

  @Test
  fun anOutdatedInstallOffersUpdate() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(
          secondDescriptor.id to RepertoireInstallState.Installed(summary = null, isOutdated = true)
        ),
    )
    onNode(hasText("UPDATE").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNode(hasText("REINSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertDoesNotExist()
  }

  @Test
  fun theInstallDialogStatesTheMovesAnUpdateDeletes() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(
          secondDescriptor.id to RepertoireInstallState.Installed(summary = null, isOutdated = true)
        ),
      previewStates =
        mapOf(
          secondDescriptor.id to
            RepertoirePreviewState.Ready(
              PgnImportPreview(totalMoves = 5, movesInCommon = 4, movesToDelete = 1)
            )
        ),
    )

    onNode(hasText("UPDATE").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .performClick()

    waitUntilNodeExists(hasText("1 move and its review progress will be deleted."))
  }

  @Test
  fun installedMasteryBarIsHiddenWithoutASnapshot() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Installed(summary = null)),
    )
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun installedMasteryBarIsHiddenWhenNoPositionIsTrainable() = runLibraryTest {
    assertInstalledMasteryBar(solid = 0, total = 0, expected = null)
  }

  @Test
  fun installedMasteryBarIsEmptyWhenNothingIsSolid() = runLibraryTest {
    assertInstalledMasteryBar(solid = 0, total = 1, expected = 0f)
  }

  @Test
  fun installedMasteryBarIsFullAtOneOfOne() = runLibraryTest {
    assertInstalledMasteryBar(solid = 1, total = 1, expected = 1f)
  }

  @Test
  fun installedMasteryBarShowsTheLowestNonZeroFraction() = runLibraryTest {
    assertInstalledMasteryBar(solid = 1, total = 200, expected = 0.005f)
  }

  @Test
  fun installedMasteryBarIsJustBelowFullAtOneShortOfTotal() = runLibraryTest {
    assertInstalledMasteryBar(solid = 199, total = 200, expected = 0.995f)
  }

  @Test
  fun installedMasteryBarHandlesALargeRepertoire() = runLibraryTest {
    assertInstalledMasteryBar(solid = 46_000, total = 92_000, expected = 0.5f)
  }

  @Test
  fun installedWithNullSummaryShowsNoSummaryButInTrainingBadge() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Installed(summary = null)),
    )
    onNode(hasText("IN TRAINING").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithText("Moves added:", substring = true).assertDoesNotExist()
  }

  @Test
  fun failedNetworkShowsErrorAndInstallRetry() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(
          secondDescriptor.id to RepertoireInstallState.Failed(InstallError.Network("offline"))
        ),
    )
    onNodeWithText("Download failed: offline").assertExists()
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun failedHttpShowsErrorAndInstallRetry() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Failed(InstallError.Http(500))),
    )
    onNodeWithText("Download failed with HTTP error 500.").assertExists()
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun failedMalformedRepertoireShowsErrorAndInstallRetry() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(
          secondDescriptor.id to
            RepertoireInstallState.Failed(InstallError.MalformedRepertoire("bad pgn"))
        ),
    )
    onNodeWithText("This repertoire could not be read: bad pgn").assertExists()
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun failedIdTakenShowsErrorAndInstallRetry() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Failed(InstallError.IdTaken)),
    )
    onNodeWithText("You already have a repertoire with this id. Rename it to install this one.")
      .assertExists()
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
  }

  @Test
  fun failedImportShowsErrorAndInstallRetry() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(
          secondDescriptor.id to
            RepertoireInstallState.Failed(InstallError.ImportFailed("import blew up"))
        ),
    )
    onNodeWithText("Import failed: import blew up").assertExists()
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun heroRendersTheSameWhenTheFallbackPickIsInstalled() = runLibraryTest {
    // A single-entry catalog where that one entry is already installed: pickHeroRepertoire's
    // exclusion has nowhere else to fall back to but this installed pick, exercising the card
    // ignoring its own install state (see HeroPackCard's own KDoc) for real, rather than trivially.
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(listOf(descriptor), isStale = false),
      installStates = mapOf(descriptor.id to RepertoireInstallState.Installed(summary = null)),
    )
    onNodeWithTag("library_hero_card").assertExists()
    onNode(hasText("PICKED FOR YOU").and(hasAnyAncestor(hasTestTag("library_hero_card"))))
      .assertExists()
    // The CTA's Text child merges into the KineticButton's own semantics node, so the tagged node
    // itself carries both the tag and the label rather than the label living on a separate
    // descendant.
    onNode(hasTestTag("library_hero_card:cta").and(hasText("Add to my training"))).assertExists()
  }

  @Test
  fun heroPicksTheMostDownloadedNotYetInstalledRepertoire() = runLibraryTest {
    // descriptor out-downloads secondDescriptor, but is already installed: the hero must be
    // secondDescriptor, the most downloaded among the ones not yet installed.
    val mostDownloaded = descriptor.copy(downloadCount = 100)
    val runnerUp = secondDescriptor.copy(downloadCount = 10)
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(listOf(mostDownloaded, runnerUp), isStale = false),
      installStates = mapOf(mostDownloaded.id to RepertoireInstallState.Installed(summary = null)),
    )
    onNode(hasText(runnerUp.name).and(hasAnyAncestor(hasTestTag("library_hero_card"))))
      .assertExists()
  }

  @Test
  fun heroFallsBackToTheOverallMostDownloadedWhenEverythingIsInstalled() = runLibraryTest {
    val mostDownloaded = descriptor.copy(downloadCount = 100)
    val runnerUp = secondDescriptor.copy(downloadCount = 10)
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(listOf(runnerUp, mostDownloaded), isStale = false),
      installStates =
        mapOf(
          mostDownloaded.id to RepertoireInstallState.Installed(summary = null),
          runnerUp.id to RepertoireInstallState.Installed(summary = null),
        ),
    )
    onNode(hasText(mostDownloaded.name).and(hasAnyAncestor(hasTestTag("library_hero_card"))))
      .assertExists()
  }

  @Test
  fun heroSkipsAnAuthoredRepertoire() = runLibraryTest {
    val mostDownloaded = descriptor.copy(downloadCount = 100)
    val runnerUp = secondDescriptor.copy(downloadCount = 10)
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(listOf(mostDownloaded, runnerUp), isStale = false),
      installStates = mapOf(mostDownloaded.id to RepertoireInstallState.Authored),
    )
    onNode(hasText(runnerUp.name).and(hasAnyAncestor(hasTestTag("library_hero_card"))))
      .assertExists()
  }

  @Test
  fun heroFallbackSkipsAnAuthoredRepertoire() = runLibraryTest {
    val authored = descriptor.copy(downloadCount = 100)
    val installed = secondDescriptor.copy(downloadCount = 10)
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(listOf(authored, installed), isStale = false),
      installStates =
        mapOf(
          authored.id to RepertoireInstallState.Authored,
          installed.id to RepertoireInstallState.Installed(summary = null),
        ),
    )
    onNode(hasText(installed.name).and(hasAnyAncestor(hasTestTag("library_hero_card"))))
      .assertExists()
  }

  @Test
  fun heroIsHiddenWhenEveryRepertoireIsAuthored() = runLibraryTest {
    setLibraryContent(
      catalogState = LibraryCatalogState.Loaded(listOf(descriptor), isStale = false),
      installStates = mapOf(descriptor.id to RepertoireInstallState.Authored),
    )
    onNodeWithTag(cardTag(descriptor)).assertExists()
    onNodeWithTag("library_hero_card").assertDoesNotExist()
  }

  @Test
  fun heroBreaksATieOnDownloadCountByCatalogOrder() = runLibraryTest {
    // Neither carries a downloadCount (the default 0, e.g. every manifest predating the field):
    // the tie falls back to catalog order, identical to the placeholder behavior this replaced.
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false)
    )
    onNode(hasText(descriptor.name).and(hasAnyAncestor(hasTestTag("library_hero_card"))))
      .assertExists()
  }

  @Test
  fun heroRequestsTheOverlapPreviewOfItsPick() = runLibraryTest {
    val requested = mutableListOf<RepertoireDescriptor>()
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      onPreviewRequest = { requested += it },
    )
    waitUntilSuspending { requested.isNotEmpty() }
    assertEquals(listOf(descriptor), requested)
  }

  @Test
  fun heroShowsTheReadyOverlap() = runLibraryTest {
    setHeroPreview(
      RepertoirePreviewState.Ready(PgnImportPreview(totalMoves = 10, movesInCommon = 3))
    )
    onNodeWithTag("library_hero_progress_bar")
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))
    onNode(hasText("3 of 10 already yours").and(hasAnyAncestor(hasTestTag("library_hero_card"))))
      .assertExists()
  }

  @Test
  fun heroShowsAnEmptyBarWhenNothingIsInCommon() = runLibraryTest {
    setHeroPreview(
      RepertoirePreviewState.Ready(PgnImportPreview(totalMoves = 1, movesInCommon = 0))
    )
    onNodeWithTag("library_hero_progress_bar")
      .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
    onNodeWithText("0 of 1 already yours").assertExists()
  }

  @Test
  fun heroShowsAFullBarWhenEverythingIsInCommon() = runLibraryTest {
    setHeroPreview(
      RepertoirePreviewState.Ready(PgnImportPreview(totalMoves = 1, movesInCommon = 1))
    )
    onNodeWithTag("library_hero_progress_bar")
      .assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
    onNodeWithText("1 of 1 already yours").assertExists()
  }

  @Test
  fun heroShowsALargeOverlap() = runLibraryTest {
    setHeroPreview(
      RepertoirePreviewState.Ready(PgnImportPreview(totalMoves = 20_000, movesInCommon = 5_000))
    )
    onNodeWithTag("library_hero_progress_bar")
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))
    onNodeWithText("5000 of 20000 already yours").assertExists()
  }

  @Test
  fun heroHidesTheOverlapForAnEmptyRepertoire() = runLibraryTest {
    setHeroPreview(
      RepertoirePreviewState.Ready(PgnImportPreview(totalMoves = 0, movesInCommon = 0))
    )
    onNodeWithTag("library_hero_card").assertExists()
    onNodeWithTag("library_hero_progress_bar").assertDoesNotExist()
    onNodeWithText("already yours", substring = true).assertDoesNotExist()
  }

  @Test
  fun heroHidesTheOverlapWhileItLoads() = runLibraryTest {
    setHeroPreview(RepertoirePreviewState.Loading)
    onNodeWithTag("library_hero_card").assertExists()
    onNodeWithTag("library_hero_progress_bar").assertDoesNotExist()
  }

  @Test
  fun heroHidesTheOverlapWhenItFailed() = runLibraryTest {
    setHeroPreview(RepertoirePreviewState.Failed(InstallError.Network("offline")))
    onNodeWithTag("library_hero_card").assertExists()
    onNodeWithTag("library_hero_progress_bar").assertDoesNotExist()
  }

  @Test
  fun heroHidesTheOverlapBeforeAnyPreviewIsPublished() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false)
    )
    onNodeWithTag("library_hero_card").assertExists()
    onNodeWithTag("library_hero_progress_bar").assertDoesNotExist()
  }

  /** Renders the two entry catalog (hero = [descriptor]) with [state] as the hero's preview. */
  private suspend fun ComposeUiTest.setHeroPreview(state: RepertoirePreviewState) =
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      previewStates = mapOf(descriptor.id to state),
    )

  private fun snapshot(solid: Int, total: Int) =
    RepertoireMasterySnapshot(solidCount = solid, totalCount = total, lastReview = null)

  /**
   * Renders [secondDescriptor] installed with a [solid] of [total] snapshot and asserts its bar
   * reads [expected], or is absent when [expected] is `null`.
   */
  private suspend fun ComposeUiTest.assertInstalledMasteryBar(
    solid: Int,
    total: Int,
    expected: Float?,
  ) {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Installed(summary = null)),
      masterySnapshots = mapOf(secondDescriptor.id to snapshot(solid, total)),
    )
    if (expected == null) {
      onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
    } else {
      onNodeWithTag(progressTag(secondDescriptor))
        .assertRangeInfoEquals(ProgressBarRangeInfo(expected, 0f..1f))
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Install-progress bar transitions (issue #282 round-2 review, extended by #309's real
  // fractions): a static render of each RepertoireInstallState (above) never exercises the
  // animated hand-off *between* states, which is exactly where a hoisted Animatable can sweep the
  // wrong way. These mutate installStates across recompositions with the clock paused, so the
  // assertions cover direction, not just the settled value.
  // ---------------------------------------------------------------------------------------------

  @Test
  fun installBarFillsForwardAndBadgeFlipsAcrossFetchingImportingInstalled() = runComposeUiTest {
    koinSetUp()
    try {
      mainClock.autoAdvance = false
      var installStates by mutableStateOf<Map<String, RepertoireInstallState>>(emptyMap())
      setTestContent {
        InitializeApp {
          RepertoireLibraryContent(
            catalogState =
              LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
            installStates = installStates,
            previewStates = emptyMap(),
            myRepertoires = emptyList(),
            actions =
              RepertoireLibraryActions(
                onInstall = {},
                onPreviewRequest = {},
                onRetry = {},
                onView = {},
              ),
          )
        }
      }
      mainClock.advanceTimeByFrame()
      onNode(hasText("NEW").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
        .assertExists()

      // Fetching(0f) -> Fetching(1f) glides 0% -> 30% over 300ms: sampled partway through, the bar
      // must have risen from empty, not merely settled somewhere below 30%.
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching(0f))
      mainClock.advanceTimeByFrame()
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching(1f))
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(100)
      mainClock.advanceTimeByFrame()
      val midFetch = currentProgress(progressTag(secondDescriptor))
      assertTrue(
        midFetch in 0f..0.30f,
        "expected the Fetching glide to be rising from 0% toward 30%, was $midFetch",
      )
      mainClock.advanceTimeBy(300)
      mainClock.advanceTimeByFrame()
      onNodeWithTag(progressTag(secondDescriptor))
        .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))

      // Importing(0f) hands off exactly at 30% (no jump), then Importing(1f) continues the glide
      // 30% -> 100%: sampled partway through, the bar must have risen past 30%, not dropped back.
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Importing(0f))
      mainClock.advanceTimeByFrame()
      onNodeWithTag(progressTag(secondDescriptor))
        .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Importing(1f))
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(100)
      mainClock.advanceTimeByFrame()
      val midImport = currentProgress(progressTag(secondDescriptor))
      assertTrue(
        midImport in 0.30f..1.00f,
        "expected the Importing glide to be rising from 30% toward 100%, was $midImport",
      )
      mainClock.advanceTimeBy(300)
      mainClock.advanceTimeByFrame()
      onNodeWithTag(progressTag(secondDescriptor))
        .assertRangeInfoEquals(ProgressBarRangeInfo(1.00f, 0f..1f))

      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Installed(summary = null))
      mainClock.advanceTimeByFrame()
      onNode(hasText("IN TRAINING").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
        .assertExists()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun installProgressRestartsAtZeroOnReinstallInsteadOfSweepingBackward() = runComposeUiTest {
    koinSetUp()
    try {
      mainClock.autoAdvance = false
      var installStates by
        mutableStateOf<Map<String, RepertoireInstallState>>(
          mapOf(secondDescriptor.id to RepertoireInstallState.Importing(1f))
        )
      setTestContent {
        InitializeApp {
          RepertoireLibraryContent(
            catalogState =
              LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
            installStates = installStates,
            previewStates = emptyMap(),
            myRepertoires = emptyList(),
            actions =
              RepertoireLibraryActions(
                onInstall = {},
                onPreviewRequest = {},
                onRetry = {},
                onView = {},
              ),
          )
        }
      }
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(300)
      mainClock.advanceTimeByFrame()
      onNodeWithTag(progressTag(secondDescriptor))
        .assertRangeInfoEquals(ProgressBarRangeInfo(1.00f, 0f..1f))

      // Reinstalling: Importing(1f) (100%) -> Fetching(0f) must restart the glide at 0%. Before
      // the fix, the hoisted Animatable animated from 1.00 down to Fetching's 0% target instead, so
      // a sample shortly after the transition would read close to 1.00 and falling.
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching(0f))
      mainClock.advanceTimeByFrame()
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching(1f))
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(100)
      mainClock.advanceTimeByFrame()
      val midReinstall = currentProgress(progressTag(secondDescriptor))
      assertTrue(
        midReinstall in 0f..0.30f,
        "expected the reinstall glide to rise from 0% toward 30%, not drain down from 100%, " +
          "was $midReinstall",
      )
      mainClock.advanceTimeBy(300)
      mainClock.advanceTimeByFrame()
      onNodeWithTag(progressTag(secondDescriptor))
        .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))
    } finally {
      koinTearDown()
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Filter chip coverage
  // ---------------------------------------------------------------------------------------------

  @Test
  fun filterChipsShowLiveCountsForMixedCatalog() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(
          listOf(descriptor, secondDescriptor, blackDescriptor),
          isStale = false,
        )
    )
    onNodeWithTag("library_filter_chip:all").assertTextEquals("All 3")
    onNodeWithTag("library_filter_chip:white").assertTextEquals("White 2")
    onNodeWithTag("library_filter_chip:black").assertTextEquals("Black 1")
  }

  @Test
  fun filterChipsShowZeroCountForAbsentColorEdgeCase() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false)
    )
    onNodeWithTag("library_filter_chip:black").assertExists().assertTextEquals("Black 0")
  }

  @Test
  fun selectingWhiteChipFiltersToWhiteOnly() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(
          listOf(descriptor, secondDescriptor, blackDescriptor),
          isStale = false,
        )
    )
    onNodeWithTag("library_filter_chip:white").performClick()
    onNodeWithTag(cardTag(descriptor)).assertExists()
    onNodeWithTag(cardTag(secondDescriptor)).assertExists()
    onNodeWithTag(cardTag(blackDescriptor)).assertDoesNotExist()
  }

  @Test
  fun selectingBlackChipFiltersToBlackOnly() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(
          listOf(descriptor, secondDescriptor, blackDescriptor),
          isStale = false,
        )
    )
    onNodeWithTag("library_filter_chip:black").performClick()
    onNodeWithTag(cardTag(blackDescriptor)).assertExists()
    onNodeWithTag(cardTag(descriptor)).assertDoesNotExist()
    onNodeWithTag(cardTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun selectingMineChipFiltersToInstalledOnly() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(
          listOf(descriptor, secondDescriptor, blackDescriptor),
          isStale = false,
        ),
      installStates =
        mapOf(secondDescriptor.id to RepertoireInstallState.Installed(summary = null)),
    )
    onNodeWithTag("library_filter_chip:mine").performClick()
    onNodeWithTag(cardTag(secondDescriptor)).assertExists()
    onNodeWithTag(cardTag(descriptor)).assertDoesNotExist()
    onNodeWithTag(cardTag(blackDescriptor)).assertDoesNotExist()
  }

  @Test
  fun allChipShowsEveryRepertoireByDefault() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(
          listOf(descriptor, secondDescriptor, blackDescriptor),
          isStale = false,
        )
    )
    onNodeWithTag(cardTag(descriptor)).assertExists()
    onNodeWithTag(cardTag(secondDescriptor)).assertExists()
    onNodeWithTag(cardTag(blackDescriptor)).assertExists()
  }

  @Test
  fun selectingChipWithZeroMatchesShowsEmptyFilterMessage() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false)
    )
    onNodeWithTag("library_filter_chip:black").performClick()
    onNodeWithTag("library_filter_empty").assertExists()
    onNodeWithTag(cardTag(descriptor)).assertDoesNotExist()
    onNodeWithTag(cardTag(secondDescriptor)).assertDoesNotExist()
  }

  @Test
  fun selectingMineChipIncludesAnAuthoredRepertoire() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Authored),
    )
    onNodeWithText("Mine 1").assertExists()
    onNodeWithTag("library_filter_chip:mine").performClick()
    onNodeWithTag(cardTag(secondDescriptor)).assertExists()
    onNodeWithTag(cardTag(descriptor)).assertDoesNotExist()
  }

  @Test
  fun selectingMineChipWithNothingInstalledShowsEmptyFilterMessage() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = emptyMap(),
    )
    onNodeWithTag("library_filter_chip:mine").performClick()
    onNodeWithTag("library_filter_empty").assertExists()
  }
}
