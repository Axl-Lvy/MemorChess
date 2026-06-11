package proj.memorchess.axl.microbenchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine

/**
 * Measures the conversions between board state and the FEN derived string forms: parsing a cropped
 * FEN back into a [GameEngine] and cropping a live position down to a [PositionKey].
 *
 * Guards against regressions in position identity handling. Every persisted node is keyed by its
 * cropped FEN, so parsing runs for each node when entering a stored position and cropping runs
 * after every move; both are also the backbone of database loading.
 */
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

  /**
   * Rebuilds a [GameEngine] from every distinct cropped FEN of the fixture graph.
   *
   * Guards the cropped FEN completion and board parsing cost paid for every position opened from
   * the database, for example when a training card is shown.
   */
  @Benchmark
  fun parseCroppedFens(bh: Blackhole) {
    for (key in positionKeys) {
      bh.consume(GameEngine(key).toFen())
    }
  }

  /**
   * Produces the [PositionKey] and the full FEN of every prebuilt position.
   *
   * Guards the FEN cropping path, including the en passant relevance check, which runs after every
   * move played anywhere in the app.
   */
  @Benchmark
  fun cropPositionsToKeys(bh: Blackhole) {
    for (engine in engines) {
      bh.consume(engine.toPositionKey())
      bh.consume(engine.toFen())
    }
  }
}
