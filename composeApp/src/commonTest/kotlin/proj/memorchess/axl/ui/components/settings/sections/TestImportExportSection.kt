package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.study.LichessStudyClient
import proj.memorchess.axl.core.data.study.LichessStudyImporter
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.waitUntilSuspending

/**
 * UI coverage for [LichessStudyImportField]'s repertoire picker, which routes to
 * [LichessStudyImporter.import]'s `repertoireId` parameter.
 */
@OptIn(ExperimentalTestApi::class)
class TestImportExportSection : TestWithKoin() {

  private val treeStore: TreeStore by inject()

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  /** A [LichessStudyImporter] backed by a canned single-chapter PGN export, no real network. */
  private fun fakeStudyImporter(): LichessStudyImporter {
    val engine = MockEngine { _ -> respond(content = "1. e4 e5 *", status = HttpStatusCode.OK) }
    val client =
      HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    return LichessStudyImporter(LichessStudyClient(client), treeStore)
  }

  @Test
  fun choosingAnExistingRepertoireTagsTheLichessImportWithIt() = runTestFromSetup {
    treeStore.registerRepertoire("italian-game", "Italian Game", RepertoireColor.WHITE)
    val studyImporter = fakeStudyImporter()
    setContent { InitializeApp { LichessStudyImportField(studyImporter, treeStore) } }
    waitForIdle()

    onNodeWithText("Italian Game").performClick()
    onNodeWithTag("lichessStudyInput").performTextInput("aaaaaaaa")
    onNodeWithTag("lichessStudyImportButton").performClick()

    // The MockEngine response dispatches on Dispatchers.Default, which the compose test clock
    // never yields to on its own, so the import's completion is polled instead of trusted to a
    // single waitForIdle() (see waitUntilBoardAppears's kdoc for the same starvation on wasmJs).
    val afterE4 = GameEngine().apply { playSanMove("e4") }.toPositionKey()
    var tags: Set<String>? = null
    waitUntilSuspending {
      withContext(Dispatchers.Default) {}
      tags = treeStore.tagsFor(PositionKey.START_POSITION, afterE4)
      tags == setOf("italian-game")
    }
    tags shouldBe setOf("italian-game")
  }
}
