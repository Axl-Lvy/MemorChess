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

/**
 * Good moves covering more positions than
 * [proj.memorchess.axl.core.graph.OpeningTree.MAX_CACHE_NODES]: every pair of first moves, each
 * followed by one white reply.
 */
fun wideRepertoireMoves(): List<RepertoireMove> {
  val white = listOf("a3", "a4", "b3", "b4", "c3", "c4", "d3", "d4", "e3", "e4")
  val whiteMore = listOf("f3", "f4", "g3", "g4", "h3", "h4", "Na3", "Nc3", "Nf3", "Nh3")
  val black = listOf("a6", "a5", "b6", "b5", "c6", "c5", "d6", "d5", "e6", "e5")
  val blackMore = listOf("f6", "f5", "g6", "g5", "h6", "h5", "Na6", "Nc6", "Nf6", "Nh6")
  val replies = listOf("Nh3", "Nf3", "Na3", "Nc3", "a3", "h3")
  val moves = linkedSetOf<RepertoireMove>()
  for (first in white + whiteMore) {
    for (second in black + blackMore) {
      val engine = GameEngine()
      for (san in listOf(first, second)) {
        val from = engine.toPositionKey().value
        moves += RepertoireMove(from, engine.playSanMove(san), engine.toPositionKey().value, true)
      }
      val from = engine.toPositionKey().value
      val reply = replies.first {
        runCatching { GameEngine(engine.toPositionKey()).playSanMove(it) }.isSuccess
      }
      moves += RepertoireMove(from, engine.playSanMove(reply), engine.toPositionKey().value, true)
    }
  }
  return moves.toList()
}
