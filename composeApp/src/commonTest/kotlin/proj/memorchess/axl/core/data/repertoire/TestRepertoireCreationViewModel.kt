package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnImportException

class TestRepertoireCreationViewModel {

  private data class Registered(val id: String, val name: String, val color: RepertoireColor?)

  private data class Imported(
    val repertoireId: String,
    val color: RepertoireColor?,
    val games: List<PgnGame>,
  )

  private data class Forked(
    val sourceId: String,
    val newId: String,
    val newName: String,
    val color: RepertoireColor?,
  )

  private fun viewModel(
    scope: TestScope,
    existingIds: suspend () -> Set<String> = { emptySet() },
    registerRepertoire: suspend (String, String, RepertoireColor?) -> Unit = { _, _, _ -> },
    importGames: suspend (String, RepertoireColor?, List<PgnGame>) -> Unit = { _, _, _ -> },
    forkRepertoire: suspend (String, String, String, RepertoireColor?) -> Unit = { _, _, _, _ -> },
  ) =
    RepertoireCreationViewModel(
      existingIds = existingIds,
      registerRepertoire = registerRepertoire,
      importGames = importGames,
      forkRepertoire = forkRepertoire,
      scope = scope,
    )

  @Test fun startsIdle() = runTest { viewModel(this).state.value shouldBe CreationState.Idle }

  @Test
  fun createRegistersASlugifiedRepertoireAndReportsDone() = runTest {
    val registered = mutableListOf<Registered>()
    val vm =
      viewModel(
        this,
        registerRepertoire = { id, name, color -> registered += Registered(id, name, color) },
      )

    vm.create(name = "Italian Game!", color = RepertoireColor.WHITE, pgnText = "")
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Done("italian-game")
    registered shouldBe listOf(Registered("italian-game", "Italian Game!", RepertoireColor.WHITE))
  }

  @Test
  fun createRejectsABlankNameWithoutRegistering() = runTest {
    val registered = mutableListOf<Registered>()
    val vm =
      viewModel(
        this,
        registerRepertoire = { id, name, color -> registered += Registered(id, name, color) },
      )

    vm.create(name = "   ", color = null, pgnText = "")
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Failed(CreationError.BlankName)
    registered shouldBe emptyList()
  }

  @Test
  fun createRejectsANameThatSlugifiesToNothing() = runTest {
    val vm = viewModel(this)

    vm.create(name = "!!!", color = null, pgnText = "")
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Failed(CreationError.BlankName)
  }

  @Test
  fun createRejectsASlugAlreadyTakenWithoutRegistering() = runTest {
    val registered = mutableListOf<Registered>()
    val vm =
      viewModel(
        this,
        existingIds = { setOf("italian-game") },
        registerRepertoire = { id, name, color -> registered += Registered(id, name, color) },
      )

    vm.create(name = "Italian Game", color = null, pgnText = "")
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Failed(CreationError.DuplicateId)
    registered shouldBe emptyList()
  }

  @Test
  fun createWithPgnTextImportsThenRegisters() = runTest {
    val calls = mutableListOf<String>()
    val imported = mutableListOf<Imported>()
    val registered = mutableListOf<Registered>()
    val vm =
      viewModel(
        this,
        importGames = { id, color, games ->
          calls += "import"
          imported += Imported(id, color, games)
        },
        registerRepertoire = { id, name, color ->
          calls += "register"
          registered += Registered(id, name, color)
        },
      )

    vm.create(name = "Italian Game", color = RepertoireColor.WHITE, pgnText = "1. e4 e5 *")
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Done("italian-game")
    calls shouldBe listOf("import", "register")
    imported.single().repertoireId shouldBe "italian-game"
    imported.single().color shouldBe RepertoireColor.WHITE
    imported.single().games.size shouldBe 1
    registered shouldBe listOf(Registered("italian-game", "Italian Game", RepertoireColor.WHITE))
  }

  @Test
  fun createWithMalformedPgnTextFailsWithoutRegistering() = runTest {
    val registered = mutableListOf<Registered>()
    val vm =
      viewModel(
        this,
        registerRepertoire = { id, name, color -> registered += Registered(id, name, color) },
      )

    vm.create(name = "Italian Game", color = null, pgnText = "1. e4 (1... c5")

    vm.state.value shouldBe CreationState.Working
    advanceUntilIdle()

    (vm.state.value as CreationState.Failed).error.shouldBeInvalidPgn()
    registered shouldBe emptyList()
  }

  @Test
  fun createWhenImportThrowsFailsWithoutRegistering() = runTest {
    val registered = mutableListOf<Registered>()
    val vm =
      viewModel(
        this,
        importGames = { _, _, _ -> throw PgnImportException("illegal move") },
        registerRepertoire = { id, name, color -> registered += Registered(id, name, color) },
      )

    vm.create(name = "Italian Game", color = null, pgnText = "1. e4 e5 *")
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Failed(CreationError.InvalidPgn("illegal move"))
    registered shouldBe emptyList()
  }

  @Test
  fun forkRegistersAndCopiesTagsThenReportsDone() = runTest {
    val forked = mutableListOf<Forked>()
    val vm =
      viewModel(
        this,
        forkRepertoire = { sourceId, newId, newName, color ->
          forked += Forked(sourceId, newId, newName, color)
        },
      )

    vm.fork(sourceId = "italian-game", newName = "Italian Game copy", color = RepertoireColor.WHITE)
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Done("italian-game-copy")
    forked shouldBe
      listOf(
        Forked("italian-game", "italian-game-copy", "Italian Game copy", RepertoireColor.WHITE)
      )
  }

  @Test
  fun forkRejectsABlankNameWithoutForking() = runTest {
    val forked = mutableListOf<Forked>()
    val vm =
      viewModel(
        this,
        forkRepertoire = { sourceId, newId, newName, color ->
          forked += Forked(sourceId, newId, newName, color)
        },
      )

    vm.fork(sourceId = "italian-game", newName = " ", color = RepertoireColor.WHITE)
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Failed(CreationError.BlankName)
    forked shouldBe emptyList()
  }

  @Test
  fun forkRejectsASlugAlreadyTakenWithoutForking() = runTest {
    val forked = mutableListOf<Forked>()
    val vm =
      viewModel(
        this,
        existingIds = { setOf("italian-game-copy") },
        forkRepertoire = { sourceId, newId, newName, color ->
          forked += Forked(sourceId, newId, newName, color)
        },
      )

    vm.fork(sourceId = "italian-game", newName = "Italian Game copy", color = RepertoireColor.WHITE)
    advanceUntilIdle()

    vm.state.value shouldBe CreationState.Failed(CreationError.DuplicateId)
    forked shouldBe emptyList()
  }

  private fun CreationError.shouldBeInvalidPgn() {
    check(this is CreationError.InvalidPgn) { "expected InvalidPgn, was $this" }
  }
}
