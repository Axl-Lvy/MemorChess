package proj.memorchess.axl.core.stockfish

class Scores(scores: DoubleArray) {
  val total: Double
  val pawn: Double
  val knight: Double
  val bishop: Double
  val rook: Double
  val queen: Double
  val king: Double
  val imbalance: Double
  val mobility: Double
  val threat: Double
  val passed: Double
  val space: Double

  init {
    if (scores.size != 12) {
      throw IllegalArgumentException("scores have invalid length: ${scores.size}")
    }

    total = scores[0]
    pawn = scores[1]
    knight = scores[2]
    bishop = scores[3]
    rook = scores[4]
    queen = scores[5]
    king = scores[6]
    imbalance = scores[7]
    mobility = scores[8]
    threat = scores[9]
    passed = scores[10]
    space = scores[11]
  }

  override fun toString(): String {
    return """
            total = %.02f
            pawn = %.02f
            knight = %.02f
            bishop = %.02f
            rook = %.02f
            queen = %.02f
            king = %.02f
            imbalance = %.02f
            mobility = %.02f
            threat = %.02f
            passed = %.02f
            space = %.02f
        """
      .trimIndent()
      .format(
        total,
        pawn,
        knight,
        bishop,
        rook,
        queen,
        king,
        imbalance,
        mobility,
        threat,
        passed,
        space,
      )
  }
}
