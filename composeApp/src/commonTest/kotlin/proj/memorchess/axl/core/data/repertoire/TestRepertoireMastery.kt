package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

class TestRepertoireMastery {

  @Test
  fun solidPercentIsZeroWhenTotalIsZero() {
    val mastery = RepertoireMastery(repertoireName = "Empty", solidCount = 0, totalCount = 0)

    mastery.solidPercent shouldBe 0
  }

  @Test
  fun solidPercentIsZeroAtTheLowestNonZeroTotal() {
    val mastery = RepertoireMastery(repertoireName = "Fresh", solidCount = 0, totalCount = 1)

    mastery.solidPercent shouldBe 0
  }

  @Test
  fun solidPercentIsHundredWhenAllPositionsAreSolid() {
    val mastery = RepertoireMastery(repertoireName = "Mastered", solidCount = 1, totalCount = 1)

    mastery.solidPercent shouldBe 100
  }

  @Test
  fun solidPercentRoundsDownBelowAWholeNumberBoundary() {
    val mastery = RepertoireMastery(repertoireName = "Boundary", solidCount = 1, totalCount = 3)

    mastery.solidPercent shouldBe 33
  }

  @Test
  fun solidPercentRoundsUpAboveAWholeNumberBoundary() {
    val mastery = RepertoireMastery(repertoireName = "Boundary", solidCount = 2, totalCount = 3)

    mastery.solidPercent shouldBe 67
  }

  @Test
  fun solidPercentRoundsAHalfPercentUpToTheNextWholeNumber() {
    val mastery = RepertoireMastery(repertoireName = "Almost", solidCount = 199, totalCount = 200)

    mastery.solidPercent shouldBe 100
  }

  @Test
  fun solidPercentOnARepresentativeLargeValue() {
    val mastery =
      RepertoireMastery(repertoireName = "Italian Game", solidCount = 46, totalCount = 68)

    mastery.solidPercent shouldBe 68
  }

  @Test
  fun noRepertoireWithATrainablePositionYieldsNoMastery() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    val tagStore = testRepertoireTagStore(database)
    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)

    mostRecentRepertoireMastery(tagStore) shouldBe null
  }

  @Test
  fun zeroSolidPositionsYieldsZeroPercent() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    val tagStore = testRepertoireTagStore(database)
    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)
    val destination = PositionKey("posA b K")
    store.addMove(PositionKey.START_POSITION, "e4", destination, isGood = true, fromDepth = 0)
    tagStore.tag(PositionKey.START_POSITION, destination, "italian-game")
    // A card mid learning (not yet REVIEW) but with a stamped lastReview: trainable, not solid.
    store.updateCardState(
      PositionKey.START_POSITION,
      CardStateFactory.new().copy(phase = CardPhase.LEARNING, lastReview = DateUtil.now()),
    )

    val mastery = mostRecentRepertoireMastery(tagStore)!!

    mastery.solidCount shouldBe 0
    mastery.solidPercent shouldBe 0
  }

  @Test
  fun allSolidPositionsYieldsHundredPercent() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    val tagStore = testRepertoireTagStore(database)
    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)
    val destination = PositionKey("posA b K")
    store.addMove(PositionKey.START_POSITION, "e4", destination, isGood = true, fromDepth = 0)
    tagStore.tag(PositionKey.START_POSITION, destination, "italian-game")
    store.updateCardState(
      PositionKey.START_POSITION,
      CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = DateUtil.now()),
    )

    val mastery = mostRecentRepertoireMastery(tagStore)!!

    mastery.solidCount shouldBe mastery.totalCount
    mastery.solidPercent shouldBe 100
  }

  @Test
  fun aTieInMostRecentActivityBreaksOnRepertoireNameAscending() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    val tagStore = testRepertoireTagStore(database)
    tagStore.register("ruy-lopez", "Ruy Lopez", RepertoireColor.WHITE)
    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)
    val destinationA = PositionKey("posA b K")
    val destinationB = PositionKey("posB b K")
    store.addMove(PositionKey.START_POSITION, "e4", destinationA, isGood = true, fromDepth = 0)
    store.addMove(PositionKey.START_POSITION, "d4", destinationB, isGood = true, fromDepth = 0)
    tagStore.tag(PositionKey.START_POSITION, destinationA, "ruy-lopez")
    tagStore.tag(PositionKey.START_POSITION, destinationB, "italian-game")
    // The start position now has one good outgoing edge in each repertoire, so a single
    // updateCardState stamps the exact same lastReview into both repertoires' trainable rows: a
    // genuine tie, not merely two separately-timed writes that happen to match.
    store.updateCardState(
      PositionKey.START_POSITION,
      CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = DateUtil.now()),
    )

    val mastery = mostRecentRepertoireMastery(tagStore)!!

    mastery.repertoireName shouldBe "Italian Game" // "Italian Game" < "Ruy Lopez" ascending
  }
}
