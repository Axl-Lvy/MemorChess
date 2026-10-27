package proj.memorchess.axl.core.sync

import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

/** Catches a broken Koin binding for [SyncApiClient] that direct construction would not. */
class TestSyncApiClientWiring : TestWithKoin() {

  private val first: SyncApiClient by inject()
  private val second: SyncApiClient by inject()

  @Test fun syncApiClientResolvesAsASingleton() = test { first shouldBeSameInstanceAs second }
}
