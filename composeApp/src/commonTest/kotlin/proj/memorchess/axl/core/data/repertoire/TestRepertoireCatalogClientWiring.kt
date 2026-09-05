package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Verifies the DI graph actually resolves a [RepertoireCatalogClient] as a singleton, catching a
 * broken Koin binding (missing dependency, wrong `baseUrl` expression) that a unit test
 * constructing it directly, like [TestRepertoireCatalogClient], would never see.
 */
class TestRepertoireCatalogClientWiring : TestWithKoin() {

  private val first: RepertoireCatalogClient by inject()
  private val second: RepertoireCatalogClient by inject()

  @Test
  fun repertoireCatalogClientResolvesAsASingleton() = test { first shouldBeSameInstanceAs second }
}
