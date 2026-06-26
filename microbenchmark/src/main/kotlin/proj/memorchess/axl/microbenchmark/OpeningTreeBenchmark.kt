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
import proj.memorchess.axl.core.graph.TreeStore

/**
 * Measures opening graph construction and the demand-paged read path through [TreeStore], the
 * single mutation chokepoint production code uses, over a repertoire sized set of positions
 * replayed from [OpeningLines].
 *
 * Since the graph became demand-paged, [TreeStore] holds only a bounded LRU of nodes and resolves
 * misses through a [proj.memorchess.axl.core.data.DatabaseQueryManager]. The benchmark backs it
 * with an [InMemoryDatabaseQueryManager] (a pure in-memory map, no platform database I/O) so the
 * read path exercises real cache hits and miss-then-rebuild resolves without disk skewing the
 * numbers.
 *
 * Guards against regressions in the immutable copy on write strategy of the cache (every edge
 * upsert rebuilds the affected nodes) and in the per-lookup resolve cost on the navigation/training
 * read path, both of which would degrade with repertoire size long before users notice.
 */
@State(Scope.Benchmark)
class OpeningTreeBenchmark {

  private var edges: List<OpeningLines.MoveEdge> = emptyList()

  /** Background scope for neighbour prefetch; supervised so a failed warm cannot abort the run. */
  private val prefetchScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  private var prebuiltStore: TreeStore = newStore()

  @Setup
  fun setup() {
    edges = OpeningLines.replayToEdges()
    prebuiltStore = buildStore()
  }

  /**
   * Builds the whole graph from scratch, edge by edge, through [TreeStore.addMove] with classified
   * moves, which includes the conversion of touched nodes to their persisted shape and the
   * write-through to the backing store.
   *
   * Guards the cost of saving a repertoire sized batch of moves, the dominant operation of the
   * initial load and of bulk imports.
   */
  @Benchmark
  fun buildTreeFromEdges(bh: Blackhole) {
    bh.consume(buildStore())
  }

  /**
   * Resolves every position of the prebuilt graph through the demand-paged read path: node access,
   * depth, membership, and the state computation the board coloring runs for the displayed
   * position.
   *
   * Guards the read path executed on every navigation step in the explorer and the trainer.
   */
  @Benchmark
  fun lookupEveryPosition(bh: Blackhole) = runBlocking {
    for (edge in edges) {
      bh.consume(prebuiltStore.node(edge.to))
      bh.consume(prebuiltStore.getDepth(edge.to))
      bh.consume(prebuiltStore.node(edge.from) != null)
      bh.consume(prebuiltStore.computeState(edge.to, edge.from))
    }
  }

  private fun newStore(): TreeStore = TreeStore(InMemoryDatabaseQueryManager(), prefetchScope)

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
