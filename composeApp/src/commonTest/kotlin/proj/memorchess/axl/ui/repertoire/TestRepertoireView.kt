package proj.memorchess.axl.ui.repertoire

import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
import proj.memorchess.axl.core.data.repertoire.CachedRepertoireCatalog
import proj.memorchess.axl.core.data.repertoire.InstallError
import proj.memorchess.axl.core.data.repertoire.LibraryCatalogState
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.data.repertoire.RepertoireInstallState
import proj.memorchess.axl.core.data.repertoire.RepertoirePreviewState
import proj.memorchess.axl.core.data.repertoire.placeholderRepertoireMastery
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertTileContainsPiece
import proj.memorchess.axl.ui.assertTileIsEmpty
import proj.memorchess.axl.ui.pages.RepertoireLibraryActions
import proj.memorchess.axl.ui.pages.RepertoireLibraryContent
import proj.memorchess.axl.ui.pages.RepertoireView
import proj.memorchess.axl.ui.playMove
import proj.memorchess.axl.ui.waitUntilBoardAppears

@OptIn(ExperimentalTestApi::class)
class TestRepertoireView : TestWithKoin() {

  private val whitePawn = ChessPiece(PieceKind.PAWN, Player.WHITE)

  private val descriptor =
    RepertoireDescriptor(
      id = "test-rep",
      name = "Test Repertoire",
      color = RepertoireColor.WHITE,
      description = "A tiny repertoire.",
      moveCount = 3,
      file = "pgn/test.pgn",
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
      file = "pgn/test2.pgn",
    )

  /** A BLACK fixture, used to exercise the color filter chips against a mixed catalog. */
  private val blackDescriptor =
    RepertoireDescriptor(
      id = "test-rep-black",
      name = "Black Repertoire",
      color = RepertoireColor.BLACK,
      description = "A black repertoire.",
      moveCount = 4,
      file = "pgn/testblack.pgn",
    )

  private fun cardTag(d: RepertoireDescriptor) = "library_repertoire_card:${d.id}"

  private fun progressTag(d: RepertoireDescriptor) = "library_progress:${d.id}"

  /** Catalog + client backed by a [MockEngine] serving a one line manifest and PGN. */
  private fun mockCatalog(
    pgn: String = "1. e4 e5 2. Nf3 *"
  ): Pair<CachedRepertoireCatalog, RepertoireCatalogClient> {
    val manifest =
      """{"schemaVersion":1,"repertoires":[{"id":"test-rep","name":"Test Repertoire",""" +
        """"color":"white","description":"A tiny repertoire.","moveCount":3,""" +
        """"file":"pgn/test.pgn"}]}"""
    val engine = MockEngine { request ->
      val path = request.url.encodedPath
      when {
        path.endsWith("manifest.json") -> respond(manifest, HttpStatusCode.OK)
        path.endsWith("test.pgn") -> respond(pgn, HttpStatusCode.OK)
        else -> respond("", HttpStatusCode.NotFound)
      }
    }
    val client = RepertoireCatalogClient(HttpClient(engine), baseUrl = "https://example.test")
    return CachedRepertoireCatalog(client) to client
  }

  private fun runViewer(
    pgn: String = "1. e4 e5 2. Nf3 *",
    block: suspend ComposeUiTest.() -> Unit,
  ) = runComposeUiTest {
    koinSetUp()
    try {
      val (catalog, client) = mockCatalog(pgn)
      setContent { InitializeApp { RepertoireView("test-rep", catalog, client) } }
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
  private fun ComposeUiTest.setLibraryContent(
    catalogState: LibraryCatalogState,
    installStates: Map<String, RepertoireInstallState> = emptyMap(),
    onInstall: (RepertoireDescriptor) -> Unit = {},
    onRetry: () -> Unit = {},
    onView: (RepertoireDescriptor) -> Unit = {},
  ) {
    setContent {
      InitializeApp {
        RepertoireLibraryContent(
          catalogState = catalogState,
          installStates = installStates,
          previewStates = emptyMap<String, RepertoirePreviewState>(),
          actions =
            RepertoireLibraryActions(
              onInstall = onInstall,
              onPreviewRequest = {},
              onRetry = onRetry,
              onView = onView,
            ),
        )
      }
    }
  }

  @Test
  fun bookMoveCanBePlayed() = runViewer {
    assertTileContainsPiece("e2", whitePawn)
    playMove("e2", "e4")
    assertTileContainsPiece("e4", whitePawn)
    assertTileIsEmpty("e2")
  }

  @Test
  fun offBookMoveIsRejected() = runViewer {
    assertTileContainsPiece("e2", whitePawn)
    // d2-d4 is legal but not in the repertoire whose only first move is e4.
    playMove("d2", "d4")
    assertTileContainsPiece("d2", whitePawn)
    assertTileIsEmpty("d4")
  }

  @Test
  fun viewerHasNoSaveOrDeleteControls() = runViewer {
    assertTileContainsPiece("e2", whitePawn)
    onNodeWithContentDescription("Save").assertDoesNotExist()
    onNodeWithContentDescription("Delete").assertDoesNotExist()
  }

  @Test
  fun cardViewButtonTriggersOnView() = runComposeUiTest {
    koinSetUp()
    try {
      var viewed: RepertoireDescriptor? = null
      setContent {
        InitializeApp {
          RepertoireLibraryContent(
            catalogState = LibraryCatalogState.Loaded(listOf(descriptor), isStale = false),
            installStates = emptyMap<String, RepertoireInstallState>(),
            previewStates = emptyMap<String, RepertoirePreviewState>(),
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
  // RepertoireInstallState coverage. Every case here uses a two entry catalog so the hero (which
  // always picks the first entry, `descriptor`) never collides on name or tag with the card under
  // test (`secondDescriptor`).
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
  fun fetchingShowsThirtyPercentProgress() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Fetching),
    )
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.30f, 0f..1f))
    onNode(hasText("Downloading…").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
  }

  @Test
  fun importingShowsHundredPercentProgress() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates = mapOf(secondDescriptor.id to RepertoireInstallState.Importing),
    )
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(ProgressBarRangeInfo(1.00f, 0f..1f))
    onNode(hasText("Importing…").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
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
    )
    onNodeWithText("Moves added: 5 · Already present: 2").assertExists()
    onNode(hasText("IN TRAINING").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithTag(progressTag(secondDescriptor))
      .assertRangeInfoEquals(
        ProgressBarRangeInfo(placeholderRepertoireMastery().solidPercent / 100f, 0f..1f)
      )
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
  fun failedMalformedPgnShowsErrorAndInstallRetry() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
      installStates =
        mapOf(
          secondDescriptor.id to RepertoireInstallState.Failed(InstallError.MalformedPgn("bad pgn"))
        ),
    )
    onNodeWithText("This repertoire file could not be read: bad pgn").assertExists()
    onNode(hasText("INSTALL").and(hasAnyAncestor(hasTestTag(cardTag(secondDescriptor)))))
      .assertExists()
    onNodeWithTag(progressTag(secondDescriptor)).assertDoesNotExist()
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
  fun heroIgnoresItsOwnInstalledState() = runLibraryTest {
    setLibraryContent(
      catalogState =
        LibraryCatalogState.Loaded(listOf(descriptor, secondDescriptor), isStale = false),
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
