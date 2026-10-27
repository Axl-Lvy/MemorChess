package proj.memorchess.axl.microbenchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine

/** Measures parsing cropped FENs into a [GameEngine] and cropping positions to a [PositionKey]. */
@State(Scope.Benchmark)
class FenBenchmark {

  private var positionKeys: List<PositionKey> = emptyList()

  private var engines: List<GameEngine> = emptyList()

  @Setup
  fun setup() {
    val edges = OpeningLines.replayToEdges()
    positionKeys =
      (listOf(PositionKey.START_POSITION) + edges.map { it.to }).distinctBy { it.value }
    engines = positionKeys.map { GameEngine(it) }
  }

  /** Rebuilds a [GameEngine] from every distinct cropped FEN of the fixture graph. */
  @Benchmark
  fun parseCroppedFens(bh: Blackhole) {
    for (key in positionKeys) {
      bh.consume(GameEngine(key).toFen())
    }
  }

  /** Produces the [PositionKey] and the full FEN of every prebuilt position. */
  @Benchmark
  fun cropPositionsToKeys(bh: Blackhole) {
    for (engine in engines) {
      bh.consume(engine.toPositionKey())
      bh.consume(engine.toFen())
    }
  }
}
