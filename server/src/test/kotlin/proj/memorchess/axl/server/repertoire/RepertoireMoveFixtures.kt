package proj.memorchess.axl.server.repertoire

import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.engine.GameEngine

/** Plays [sans] from the starting position; moves at an even ply are marked good, odd ones bad. */
internal fun line(vararg sans: String): List<RepertoireMove> {
  val engine = GameEngine()
  return sans.mapIndexed { ply, san ->
    val from = engine.toPositionKey().value
    val played = engine.playSanMove(san)
    RepertoireMove(from, played, engine.toPositionKey().value, isGood = ply % 2 == 0)
  }
}

/** The distinct moves of every one of [lines]. */
internal fun repertoire(vararg lines: List<RepertoireMove>): List<RepertoireMove> =
  lines.flatMap { it }.distinct()
