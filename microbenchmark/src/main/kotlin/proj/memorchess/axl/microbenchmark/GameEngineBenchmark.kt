package proj.memorchess.axl.microbenchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import proj.memorchess.axl.core.engine.GameEngine

/**
 * Measures [GameEngine]'s SAN move validation and FEN production, which run on every move a user
 * plays.
 */
@State(Scope.Benchmark)
class GameEngineBenchmark {

  /** Plays every fixture line from the starting position and reads the final FEN. */
  @Benchmark
  fun playSanLines(bh: Blackhole) {
    for (line in OpeningLines.SAN_LINES) {
      val engine = GameEngine()
      for (san in line) {
        engine.playSanMove(san)
      }
      bh.consume(engine.toFen())
    }
  }

  /**
   * Plays every fixture line, deriving a [proj.memorchess.axl.core.data.PositionKey] after each ply
   * as the explorer does.
   */
  @Benchmark
  fun positionKeyAfterEveryMove(bh: Blackhole) {
    for (line in OpeningLines.SAN_LINES) {
      val engine = GameEngine()
      for (san in line) {
        engine.playSanMove(san)
        bh.consume(engine.toPositionKey())
      }
    }
  }
}
