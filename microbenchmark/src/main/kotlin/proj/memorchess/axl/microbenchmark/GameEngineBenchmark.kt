package proj.memorchess.axl.microbenchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import proj.memorchess.axl.core.engine.GameEngine

/**
 * Measures [GameEngine] on the hot path of both exploration and training: validating and applying
 * SAN moves, then producing the position identity used to key the opening graph.
 *
 * Guards against regressions in move validation and FEN production, the two operations executed on
 * every single move a user plays. A slowdown here multiplies across the whole app because every
 * interaction controller funnels moves through [GameEngine].
 */
@State(Scope.Benchmark)
class GameEngineBenchmark {

  /**
   * Plays all fixture lines move by move from the starting position and reads the final FEN.
   *
   * Guards the SAN parsing and legal move generation cost of [GameEngine.playSanMove], the exact
   * work done when replaying stored lines.
   */
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
   * Plays all fixture lines and derives a [proj.memorchess.axl.core.data.PositionKey] after every
   * ply, exactly as the explorer does to look up each reached position in the opening graph.
   *
   * Guards the combined cost of move application plus FEN cropping, including the en passant
   * relevance check.
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
