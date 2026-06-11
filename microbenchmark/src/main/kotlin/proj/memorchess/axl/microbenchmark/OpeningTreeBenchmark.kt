package proj.memorchess.axl.microbenchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking
import proj.memorchess.axl.core.graph.OpeningTree
import proj.memorchess.axl.core.graph.TreeStore

/**
 * Measures opening graph construction and lookup through [TreeStore], the single mutation
 * chokepoint production code uses, over a repertoire sized set of positions replayed from
 * [OpeningLines].
 *
 * Guards against regressions in the immutable copy on write strategy of [OpeningTree]: every edge
 * upsert rebuilds the affected nodes and replaces the internal map, so an accidental change from
 * structural sharing to full copies would degrade quadratically with repertoire size and show up
 * here long before users with large repertoires notice it.
 */
@State(Scope.Benchmark)
class OpeningTreeBenchmark {

  private var edges: List<OpeningLines.MoveEdge> = emptyList()

  private var prebuiltTree: OpeningTree = OpeningTree()

  @Setup
  fun setup() {
    edges = OpeningLines.replayToEdges()
    prebuiltTree = buildStore().current()
  }

  /**
   * Builds the whole graph from scratch, edge by edge, through [TreeStore.addMove] with classified
   * moves, which includes the conversion of touched nodes to their persisted shape.
   *
   * Guards the cost of saving a repertoire sized batch of moves, the dominant operation of the
   * initial load and of bulk imports.
   */
  @Benchmark
  fun buildTreeFromEdges(bh: Blackhole) {
    val store = buildStore()
    bh.consume(store.current().snapshot().size)
  }

  /**
   * Looks up every position of the prebuilt graph: node access, depth, membership, and the state
   * computation the board coloring runs for the displayed position.
   *
   * Guards the read path executed on every navigation step in the explorer and the trainer.
   */
  @Benchmark
  fun lookupEveryPosition(bh: Blackhole) {
    for (edge in edges) {
      bh.consume(prebuiltTree.get(edge.to))
      bh.consume(prebuiltTree.getDepth(edge.to))
      bh.consume(prebuiltTree.isKnown(edge.from))
      bh.consume(prebuiltTree.computeState(edge.to, edge.from))
    }
  }

  private fun buildStore(): TreeStore {
    val store = TreeStore(NoOpDatabaseQueryManager())
    runBlocking {
      for (edge in edges) {
        store.addMove(edge.from, edge.san, edge.to, isGood = true, fromDepth = edge.fromDepth)
      }
    }
    return store
  }
}
