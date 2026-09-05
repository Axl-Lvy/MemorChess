package proj.memorchess.axl.core.sync

import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Verifies the DI graph actually resolves a [SyncApiClient] as a singleton, catching a broken Koin
 * binding (missing dependency, wrong `baseUrl` expression) that a unit test constructing it
 * directly, like [TestSyncApiClient], would never see.
 */
class TestSyncApiClientWiring : TestWithKoin() {

  private val first: SyncApiClient by inject()
  private val second: SyncApiClient by inject()

  @Test fun syncApiClientResolvesAsASingleton() = test { first shouldBeSameInstanceAs second }
}
