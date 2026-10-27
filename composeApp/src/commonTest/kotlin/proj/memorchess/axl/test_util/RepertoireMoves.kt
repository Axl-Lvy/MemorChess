package proj.memorchess.axl.test_util

import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.RepertoireMoveList
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.pgn.PgnMoveNode
import proj.memorchess.axl.core.pgn.PgnParser

/** The distinct moves of every variation of [pgns], each marked good. */
fun repertoireMovesOf(vararg pgns: String): List<RepertoireMove> {
  val moves = linkedSetOf<RepertoireMove>()
  fun walk(from: String, node: PgnMoveNode) {
    val engine = GameEngine(proj.memorchess.axl.core.data.PositionKey(from))
    val played = engine.playSanMove(node.san)
    val to = engine.toPositionKey().value
    moves += RepertoireMove(from, played, to, isGood = true)
    node.children.forEach { walk(to, it) }
  }
  val start = GameEngine().toPositionKey().value
  pgns.flatMap { PgnParser.parse(it) }.forEach { game -> game.moves.forEach { walk(start, it) } }
  return moves.toList()
}

/** The body the server answers a moves download with, for [pgns]. */
fun repertoireMovesJsonOf(vararg pgns: String): String =
  Json.encodeToString(RepertoireMoveList(repertoireMovesOf(*pgns)))
