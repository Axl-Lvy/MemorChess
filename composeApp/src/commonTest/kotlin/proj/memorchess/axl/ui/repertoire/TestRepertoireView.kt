package proj.memorchess.axl.ui.repertoire

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import proj.memorchess.axl.core.data.repertoire.CachedRepertoireCatalog
import proj.memorchess.axl.core.data.repertoire.LibraryCatalogState
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.data.repertoire.RepertoireInstallState
import proj.memorchess.axl.core.data.repertoire.RepertoirePreviewState
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
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
}
