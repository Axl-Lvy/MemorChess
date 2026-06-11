package proj.memorchess.axl.microbenchmark

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine

/**
 * Deterministic benchmark fixture: sixteen well known opening main lines, twenty plies each, given
 * in marker free SAN exactly as the app stores moves.
 *
 * The fixture is the single source of inputs for every benchmark in this module so that runs stay
 * comparable across machines and over time. Changing a line invalidates historical comparisons; add
 * new lines instead of editing existing ones.
 */
object OpeningLines {

  /** One entry per opening, each a sequence of SAN moves playable from the starting position. */
  val SAN_LINES: List<List<String>> =
    listOf(
        // Ruy Lopez, Breyer
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Nb8 d4 Nbd7",
        // Italian, Giuoco Pianissimo
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3 d6 O-O a6 a4 Ba7 Re1 O-O h3 Be6 Bxe6 fxe6",
        // Sicilian, Najdorf English Attack
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 e5 Nb3 Be6 f3 Be7 Qd2 O-O O-O-O Nbd7",
        // Sicilian, Sveshnikov
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5 Ndb5 d6 Bg5 a6 Na3 b5 Nd5 Be7 Bxf6 Bxf6",
        // French, Winawer poisoned pawn
        "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3 bxc3 Ne7 Qg4 Qc7 Qxg7 Rg8 Qxh7 cxd4 Ne2 Nbc6",
        // Caro Kann, Classical
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6 Nf3 Nd7 h5 Bh7 Bd3 Bxd3 Qxd3 e6",
        // Queen's Gambit Declined, Tartakower
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 h6 Bh4 b6 Be2 Bb7 Bxf6 Bxf6 cxd5 exd5",
        // Slav, Czech main line
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4 a4 Bf5 e3 e6 Bxc4 Bb4 O-O O-O Qe2 Nbd7",
        // King's Indian, Classical
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7 Ne1 Nd7 Be3 f5",
        // Nimzo Indian, Rubinstein
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O Nc6 a3 Bxc3 bxc3 dxc4 Bxc4 Qc7",
        // Gruenfeld, Exchange
        "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7 Nf3 c5 Rb1 O-O Be2 cxd4 cxd4 Qa5",
        // English, Four Knights reversed dragon
        "c4 e5 Nc3 Nf6 Nf3 Nc6 g3 d5 cxd5 Nxd5 Bg2 Nb6 O-O Be7 d3 O-O a3 Be6 b4 a5",
        // London System
        "d4 d5 Bf4 Nf6 e3 c5 Nf3 Nc6 c3 e6 Nbd2 Bd6 Bg3 O-O Bd3 b6 e4 dxe4 Nxe4 Be7",
        // Scotch, Mieses
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5 Qe7 Qe2 Nd5 c4 Ba6 b3 g6 g3 Bg7",
        // Catalan, Open
        "d4 Nf6 c4 e6 g3 d5 Bg2 Be7 Nf3 O-O O-O dxc4 Qc2 a6 Qxc4 b5 Qc2 Bb7 Bd2 Be4",
        // Petroff, Classical
        "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4 d4 d5 Bd3 Nc6 O-O Be7 c4 Nb4 Be2 O-O Nc3 Bf5",
      )
      .map { it.split(" ") }

  /**
   * One graph edge produced by replaying the fixture lines.
   *
   * @property from Position the move is played from.
   * @property san The move in marker free SAN.
   * @property to Position reached after the move.
   * @property fromDepth Ply distance of [from] from the starting position.
   */
  data class MoveEdge(
    val from: PositionKey,
    val san: String,
    val to: PositionKey,
    val fromDepth: Int,
  )

  /**
   * Replays every line through [GameEngine] and returns the deduplicated edge list, in first
   * occurrence order. Lines share their early plies, so the result is a tree shaped graph of a few
   * hundred positions, the realistic size of a personal opening repertoire.
   */
  fun replayToEdges(): List<MoveEdge> {
    val edges = LinkedHashMap<Pair<PositionKey, String>, MoveEdge>()
    for (line in SAN_LINES) {
      val engine = GameEngine()
      var fromKey = engine.toPositionKey()
      line.forEachIndexed { ply, san ->
        engine.playSanMove(san)
        val toKey = engine.toPositionKey()
        edges.getOrPut(fromKey to san) { MoveEdge(fromKey, san, toKey, ply) }
        fromKey = toKey
      }
    }
    return edges.values.toList()
  }
}
