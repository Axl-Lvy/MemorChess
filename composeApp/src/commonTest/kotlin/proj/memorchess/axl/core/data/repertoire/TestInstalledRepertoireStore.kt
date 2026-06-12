package proj.memorchess.axl.core.data.repertoire

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.config.INSTALLED_REPERTOIRES_SETTING
import proj.memorchess.axl.test_util.TestWithKoin

class TestInstalledRepertoireStore : TestWithKoin() {

  override suspend fun setUp() {
    INSTALLED_REPERTOIRES_SETTING.reset()
  }

  @Test
  fun nothingIsInstalledByDefault() = test {
    val store = InstalledRepertoireStore()

    store.installedIds().shouldBeEmpty()
    store.isInstalled("london-system-white") shouldBe false
  }

  @Test
  fun markInstalledRoundTrips() = test {
    val store = InstalledRepertoireStore()

    store.markInstalled("london-system-white")
    store.markInstalled("caro-kann-black")

    store.isInstalled("london-system-white") shouldBe true
    store.isInstalled("caro-kann-black") shouldBe true
    store.isInstalled("vienna-gambit-white") shouldBe false
    store.installedIds() shouldBe setOf("london-system-white", "caro-kann-black")
  }

  @Test
  fun markInstalledIsIdempotent() = test {
    val store = InstalledRepertoireStore()

    store.markInstalled("london-system-white")
    store.markInstalled("london-system-white")

    store.installedIds() shouldBe setOf("london-system-white")
  }

  @Test
  fun unmarkInstalledRemovesOnlyTheGivenId() = test {
    val store = InstalledRepertoireStore()
    store.markInstalled("london-system-white")
    store.markInstalled("caro-kann-black")

    store.unmarkInstalled("london-system-white")

    store.isInstalled("london-system-white") shouldBe false
    store.installedIds() shouldBe setOf("caro-kann-black")
  }

  @Test
  fun unmarkInstalledOnMissingIdIsANoOp() = test {
    val store = InstalledRepertoireStore()
    store.markInstalled("caro-kann-black")

    store.unmarkInstalled("never-installed")

    store.installedIds() shouldBe setOf("caro-kann-black")
  }

  @Test
  fun blankOrSeparatorContainingIdsAreRejected() = test {
    val store = InstalledRepertoireStore()

    shouldThrow<IllegalArgumentException> { store.markInstalled("") }
    shouldThrow<IllegalArgumentException> { store.markInstalled("a,b") }
  }

  @Test
  fun settingsResetClearsInstalledIds() = test {
    val store = InstalledRepertoireStore()
    store.markInstalled("london-system-white")

    ALL_SETTINGS_ITEMS.forEach { it.reset() }

    store.installedIds().shouldBeEmpty()
    store.isInstalled("london-system-white") shouldBe false
  }
}
