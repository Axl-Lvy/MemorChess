package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Checks Koin resolves [RepertoireCatalogClient] as a singleton, catching broken bindings that a
 * directly constructed client never exercises.
 */
class TestRepertoireCatalogClientWiring : TestWithKoin() {

  private val first: RepertoireCatalogClient by inject()
  private val second: RepertoireCatalogClient by inject()

  @Test
  fun repertoireCatalogClientResolvesAsASingleton() = test { first shouldBeSameInstanceAs second }
}
