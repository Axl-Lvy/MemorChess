package proj.memorchess.axl.microbenchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.PositionKeyCodec

/**
 * Measures packing position keys into their binary storage form and back.
 *
 * Every database read and write of a position runs these conversions, so their cost adds directly
 * to each node fetch on top of the SQL itself.
 */
@State(Scope.Benchmark)
class PositionKeyCodecBenchmark {

  private var positionKeys: List<PositionKey> = emptyList()

  private var encodedKeys: List<ByteArray> = emptyList()

  @Setup
  fun setup() {
    val edges = OpeningLines.replayToEdges()
    positionKeys =
      (listOf(PositionKey.START_POSITION) + edges.map { it.to }).distinctBy { it.value }
    encodedKeys = positionKeys.map { PositionKeyCodec.encode(it) }
  }

  /** Encodes every distinct key of the fixture graph, as each database lookup and write does. */
  @Benchmark
  fun encodeAll(bh: Blackhole) {
    for (key in positionKeys) {
      bh.consume(PositionKeyCodec.encode(key))
    }
  }

  /** Decodes every stored key of the fixture graph, as each row read back from disk does. */
  @Benchmark
  fun decodeAll(bh: Blackhole) {
    for (bytes in encodedKeys) {
      bh.consume(PositionKeyCodec.decode(bytes))
    }
  }
}
