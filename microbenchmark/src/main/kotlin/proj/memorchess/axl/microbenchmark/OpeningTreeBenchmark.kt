package proj.memorchess.axl.microbenchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.Prefetcher
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.sync.DeviceIdentity

/**
 * Measures building the opening graph through [TreeStore] and its demand-paged read path, backed by
 * an [InMemoryDatabaseQueryManager] so disk I/O does not skew the numbers.
 */
@State(Scope.Benchmark)
class OpeningTreeBenchmark {

  private var edges: List<OpeningLines.MoveEdge> = emptyList()

  /** Runs every cache load; supervised so one failed load cannot abort the run. */
  private val loadScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  private var prebuiltStore: TreeStore = newStore()

  @Setup
  fun setup() {
    edges = OpeningLines.replayToEdges()
    prebuiltStore = buildStore()
  }

  /** Builds the whole graph edge by edge through [TreeStore.addMove], as a bulk import does. */
  @Benchmark
  fun buildTreeFromEdges(bh: Blackhole) {
    bh.consume(buildStore())
  }

  /** Resolves every position through the read path each explorer and trainer step runs. */
  @Benchmark
  fun lookupEveryPosition(bh: Blackhole) = runBlocking {
    for (edge in edges) {
      bh.consume(prebuiltStore.node(edge.to))
      bh.consume(prebuiltStore.getDepth(edge.to))
      bh.consume(prebuiltStore.node(edge.from) != null)
      bh.consume(prebuiltStore.computeState(edge.to, edge.from))
    }
  }

  private fun newStore(): TreeStore {
    val database = InMemoryDatabaseQueryManager()
    val deviceIdentity = DeviceIdentity.ephemeral()
    val cache = NodeCache({ database.getPosition(it) }, loadScope)
    val trainable = TrainableProjection(database, cache)
    return TreeStore(
      database,
      cache,
      Prefetcher(cache, loadScope),
      trainable,
      RepertoireTagStore(database, deviceIdentity, trainable),
      deviceIdentity,
    )
  }

  private fun buildStore(): TreeStore {
    val store = newStore()
    runBlocking {
      for (edge in edges) {
        store.addMove(edge.from, edge.san, edge.to, isGood = true, fromDepth = edge.fromDepth)
      }
    }
    return store
  }
}
