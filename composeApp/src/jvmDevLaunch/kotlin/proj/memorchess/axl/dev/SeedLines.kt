package proj.memorchess.axl.dev

import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.engine.Player

/** One repertoire of the dev seed: its identity and the opening lines it teaches, as PGN. */
internal data class SeedRepertoire(
  val id: String,
  val name: String,
  val color: RepertoireColor,
  val perspective: Player,
  val pgn: String,
)

private fun games(vararg lines: String): String =
  lines.joinToString("\n\n") { "[Event \"Dev seed\"]\n\n$it *" }

/** The repertoires a dev database is seeded with: one for White and two for Black. */
internal val SEED_REPERTOIRES: List<SeedRepertoire> =
  listOf(
    SeedRepertoire(
      id = "dev-white",
      name = "White: Vienna & London",
      color = RepertoireColor.WHITE,
      perspective = Player.WHITE,
      pgn =
        games(
          "1. e4 e5 2. Nc3 Nf6 3. f4 d5 4. fxe5 Nxe4 5. Nf3 Be7 6. d4 O-O 7. Bd3 f5 8. exf6 Bxf6",
          "1. e4 e5 2. Nc3 Nf6 3. f4 d5 4. fxe5 Nxe4 5. Nf3 Bg4 6. Qe2 Nxc3 7. dxc3 c5",
          "1. e4 e5 2. Nc3 Nf6 3. f4 d5 4. fxe5 Nxe4 5. Nf3 Nc6 6. Bb5 Bc5 7. d4 Bb4 8. Bd2",
          "1. e4 e5 2. Nc3 Nf6 3. f4 exf4 4. e5 Ng8 5. Nf3 d6 6. d4 dxe5 7. Qe2 Bb4 8. Qxe5+ Qe7",
          "1. e4 e5 2. Nc3 Nf6 3. f4 exf4 4. e5 Qe7 5. Qe2 Ng8 6. Nf3 d6 7. d4 dxe5 8. dxe5 Nc6",
          "1. e4 e5 2. Nc3 Nf6 3. f4 d6 4. Nf3 Nc6 5. Bb5 Bd7 6. d3 a6 7. Bxc6 Bxc6 8. O-O",
          "1. e4 e5 2. Nc3 Nc6 3. Bc4 Nf6 4. d3 Bc5 5. f4 d6 6. Nf3 Ng4 7. Ng5 O-O 8. f5",
          "1. e4 e5 2. Nc3 Nc6 3. Bc4 Bc5 4. Qg4 g6 5. Qg3 d6 6. Na4 Bb6 7. Nxb6 axb6 8. d3",
          "1. e4 e5 2. Nc3 Nc6 3. Bc4 Nf6 4. d3 Na5 5. Nge2 Nxc4 6. dxc4 Bc5 7. O-O d6 8. h3",
          "1. e4 e5 2. Nc3 Bc5 3. Nf3 d6 4. d4 exd4 5. Nxd4 Nf6 6. Bg5 h6 7. Bh4 O-O 8. Be2",
          "1. d4 d5 2. Bf4 Nf6 3. e3 e6 4. Nf3 Bd6 5. Bg3 O-O 6. Bd3 c5 7. c3 Nc6 8. Nbd2",
          "1. d4 d5 2. Bf4 Nf6 3. e3 c5 4. c3 Nc6 5. Nd2 e6 6. Ngf3 Bd6 7. Bg3 O-O 8. Bd3",
          "1. d4 d5 2. Bf4 Nf6 3. e3 Bf5 4. c4 e6 5. Nc3 Bb4 6. Qb3 Nc6 7. Nf3 O-O 8. Be2",
          "1. d4 d5 2. Bf4 c5 3. e3 Nc6 4. c3 Qb6 5. Qb3 c4 6. Qc2 Bf5 7. Qc1 e6 8. Nd2",
          "1. d4 Nf6 2. Bf4 g6 3. e3 Bg7 4. Nf3 O-O 5. Be2 d6 6. h3 Nbd7 7. O-O c5 8. c3",
          "1. d4 Nf6 2. Bf4 e6 3. e3 c5 4. c3 Nc6 5. Nd2 d5 6. Ngf3 Bd6 7. Bg3 O-O 8. Bd3",
          "1. d4 Nf6 2. Bf4 d6 3. e3 g6 4. Nf3 Bg7 5. h3 O-O 6. Be2 c5 7. c3 Nc6 8. O-O",
          "1. d4 d5 2. Bf4 e6 3. e3 Bd6 4. Bg3 Nf6 5. Nd2 c5 6. c3 Nc6 7. Bd3 O-O 8. Ngf3",
        ),
    ),
    SeedRepertoire(
      id = "dev-black-e4",
      name = "Black vs 1.e4: Scandinavian",
      color = RepertoireColor.BLACK,
      perspective = Player.BLACK,
      pgn =
        games(
          "1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. d4 Nf6 5. Nf3 c6 6. Bc4 Bf5 7. Bd2 e6 8. Qe2 Bb4",
          "1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. d4 Nf6 5. Bc4 c6 6. Nf3 Bf5 7. Bd2 e6 8. O-O Bb4",
          "1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. Nf3 Nf6 5. d4 c6 6. Bc4 Bf5 7. Ne5 e6 8. g4 Bg6",
          "1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. Nf3 Nf6 5. Bc4 c6 6. O-O Bg4 7. h3 Bh5 8. d4 e6",
          "1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. Bc4 Nf6 5. d3 c6 6. Bd2 Bf5 7. Nf3 e6 8. O-O Qc7",
          "1. e4 d5 2. exd5 Qxd5 3. Nf3 Bg4 4. Be2 Nc6 5. O-O O-O-O 6. c3 e5 7. h3 Bh5 8. d3 f6",
          "1. e4 d5 2. exd5 Qxd5 3. d4 Nf6 4. Nf3 Bf5 5. c4 Qa5+ 6. Bd2 Qb6 7. Qb3 c6 8. Nc3 e6",
          "1. e4 d5 2. exd5 Qxd5 3. Nc3 Qa5 4. d4 c6 5. Nf3 Nf6 6. Bd2 Bf5 7. Bc4 e6 8. Ne5 Nbd7",
          "1. e4 d5 2. Nc3 d4 3. Nce2 e5 4. Ng3 Be6 5. Nf3 f6 6. Bb5+ c6 7. Ba4 Nd7 8. O-O Qc7",
          "1. e4 d5 2. e5 Bf5 3. d4 e6 4. Nf3 c5 5. c3 Nc6 6. Be2 Nge7 7. O-O Ng6 8. Na3 cxd4",
          "1. e4 d5 2. d3 dxe4 3. dxe4 Qxd1+ 4. Kxd1 e5 5. Nf3 Nc6 6. Bb5 Bd7 7. Nc3 O-O-O 8. Be3 f6",
        ),
    ),
    SeedRepertoire(
      id = "dev-black-d4",
      name = "Black vs 1.d4: Queen's Gambit Declined",
      color = RepertoireColor.BLACK,
      perspective = Player.BLACK,
      pgn =
        games(
          "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. e3 O-O 6. Nf3 h6 7. Bh4 b6 8. cxd5 Nxd5",
          "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. e3 O-O 6. Nf3 h6 7. Bxf6 Bxf6 8. Rc1 c6",
          "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. cxd5 exd5 5. Bg5 Be7 6. e3 c6 7. Qc2 Nbd7 8. Bd3 O-O",
          "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Nf3 Be7 5. Bf4 O-O 6. e3 c5 7. dxc5 Bxc5 8. Qc2 Nc6",
          "1. d4 d5 2. c4 e6 3. Nf3 Nf6 4. g3 Be7 5. Bg2 O-O 6. O-O dxc4 7. Qc2 a6 8. Qxc4 b5",
          "1. d4 d5 2. c4 e6 3. Nf3 Nf6 4. Bg5 Bb4+ 5. Nc3 h6 6. Bxf6 Qxf6 7. e3 O-O 8. Rc1 dxc4",
          "1. d4 d5 2. Nf3 Nf6 3. Bf4 e6 4. e3 c5 5. c3 Nc6 6. Nbd2 Bd6 7. Bg3 O-O 8. Bd3 b6",
          "1. d4 d5 2. Bf4 Nf6 3. e3 e6 4. Nd2 c5 5. c3 Nc6 6. Ngf3 Bd6 7. Bg3 O-O 8. Bd3 b6",
          "1. d4 d5 2. c4 e6 3. Nc3 Be7 4. Nf3 Nf6 5. Bg5 h6 6. Bh4 O-O 7. e3 b6 8. Be2 Bb7",
          "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Nbd7 5. e3 c6 6. Nf3 Qa5 7. Nd2 Bb4 8. Qc2 O-O",
          "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Nbd7 5. cxd5 exd5 6. Nxd5 Nxd5 7. Bxd8 Bb4+ 8. Qd2 Bxd2+",
          "1. d4 d5 2. c4 e6 3. e3 Nf6 4. Nc3 Be7 5. Nf3 O-O 6. Bd3 dxc4 7. Bxc4 c5 8. O-O a6",
        ),
    ),
  )
