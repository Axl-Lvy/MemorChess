package proj.memorchess.axl.core.data.repertoire

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.config.PUBLISHED_REPERTOIRES_SETTING
import proj.memorchess.axl.test_util.TestWithKoin

class TestPublishedRepertoireStore : TestWithKoin() {

  override suspend fun setUp() {
    PUBLISHED_REPERTOIRES_SETTING.reset()
  }

  @Test
  fun aNeverPublishedIdHasNoSlug() = test {
    val store = PublishedRepertoireStore()

    store.publishedSlug("italian-game").shouldBeNull()
  }

  @Test
  fun recordPublishedThenPublishedSlugReturnsIt() = test {
    val store = PublishedRepertoireStore()

    store.recordPublished("italian-game", "my-italian-game")

    store.publishedSlug("italian-game") shouldBe "my-italian-game"
  }

  @Test
  fun clearPublishedRemovesTheSlug() = test {
    val store = PublishedRepertoireStore()
    store.recordPublished("italian-game", "my-italian-game")

    store.clearPublished("italian-game")

    store.publishedSlug("italian-game").shouldBeNull()
  }

  @Test
  fun settingsResetClearsPublishedSlugs() = test {
    val store = PublishedRepertoireStore()
    store.recordPublished("italian-game", "my-italian-game")

    ALL_SETTINGS_ITEMS.forEach { it.reset() }

    store.publishedSlug("italian-game").shouldBeNull()
  }

  @Test
  fun tracksMultipleRepertoiresIndependently() = test {
    val store = PublishedRepertoireStore()

    store.recordPublished("italian-game", "my-italian-game")
    store.recordPublished("caro-kann", "my-caro-kann")

    store.publishedSlug("italian-game") shouldBe "my-italian-game"
    store.publishedSlug("caro-kann") shouldBe "my-caro-kann"
  }

  @Test
  fun clearingOneRepertoireLeavesTheOtherIntact() = test {
    val store = PublishedRepertoireStore()
    store.recordPublished("italian-game", "my-italian-game")
    store.recordPublished("caro-kann", "my-caro-kann")

    store.clearPublished("italian-game")

    store.publishedSlug("italian-game").shouldBeNull()
    store.publishedSlug("caro-kann") shouldBe "my-caro-kann"
  }

  @Test
  fun republishingUnderTheSameLocalIdReplacesTheSlug() = test {
    val store = PublishedRepertoireStore()
    store.recordPublished("italian-game", "my-italian-game")

    store.recordPublished("italian-game", "my-italian-game-v2")

    store.publishedSlug("italian-game") shouldBe "my-italian-game-v2"
  }

  @Test
  fun recordPublishedRejectsABlankLocalId() = test {
    val store = PublishedRepertoireStore()

    shouldThrow<IllegalArgumentException> { store.recordPublished("", "my-italian-game") }
  }

  @Test
  fun recordPublishedRejectsALocalIdContainingTheSeparator() = test {
    val store = PublishedRepertoireStore()

    shouldThrow<IllegalArgumentException> {
      store.recordPublished("italian,game", "my-italian-game")
    }
  }

  @Test
  fun recordPublishedRejectsALocalIdContainingThePairSeparator() = test {
    val store = PublishedRepertoireStore()

    shouldThrow<IllegalArgumentException> {
      store.recordPublished("italian=game", "my-italian-game")
    }
  }

  @Test
  fun recordPublishedRejectsASlugContainingTheSeparator() = test {
    val store = PublishedRepertoireStore()

    shouldThrow<IllegalArgumentException> {
      store.recordPublished("italian-game", "my,italian-game")
    }
  }

  @Test
  fun recordPublishedRejectsASlugContainingThePairSeparator() = test {
    val store = PublishedRepertoireStore()

    shouldThrow<IllegalArgumentException> {
      store.recordPublished("italian-game", "my=italian-game")
    }
  }
}
