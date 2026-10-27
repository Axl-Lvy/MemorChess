package proj.memorchess.axl.core.interactions

import org.koin.core.component.inject
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.BoardUtils
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.Edge
import proj.memorchess.axl.core.graph.Node
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.scheduling.ReviewGrade

/**
 * Drills a single position by asking the user to play one of its good outgoing moves.
 *
 * @property repertoireScope When not `null`, only moves also tagged with this repertoire are
 *   accepted.
 * @property callBackOnCorrect Called after each move with the correct [Edge] or `null`, the played
 *   square, and, for a wrong attempt with an in-scope good edge, the square it should have reached.
 */
class SingleMoveTrainer(
  private var node: Node,
  private val repertoireScope: String? = null,
  private val callBackOnCorrect: (Edge?, BoardLocation, BoardLocation?) -> Unit,
) : InteractionsManager(GameEngine(node.positionKey)) {

  private val trainingScheduler: TrainingScheduler by inject()
  private val treeStore: TreeStore by inject()
  private val tagStore: RepertoireTagStore by inject()

  // Set once the card is graded AGAIN, so nothing played or asked for afterwards grades it again.
  private var failed = false
  private var answered = false

  override suspend fun afterPlayMove(move: String) {
    answered = true
    // Matched by resulting position, not SAN text, so a stored over-disambiguated "Ngf3" still
    // accepts the "Nf3" the board plays.
    val playedPosition = engine.toPositionKey()
    val matchingEdge =
      node.outgoing.values.firstOrNull {
        it.to == playedPosition && it.isGood == true && isInScope(it)
      }
    val isCorrect = matchingEdge != null

    val mover = engine.playerTurn.opponent // engine already advanced past this move
    val playedSquare = BoardUtils.destinationSquare(move, mover)
    val correctSquare =
      if (isCorrect) null
      else expectedEdge()?.let { BoardUtils.destinationSquare(it.move, mover) }

    block()
    // Settled before grading suspends, so a retry() racing the grade cannot drop the failure.
    val alreadyFailed = failed
    if (!isCorrect) failed = true
    callBackOnCorrect(matchingEdge, playedSquare, correctSquare)
    if (!alreadyFailed) {
      trainingScheduler.grade(
        node.positionKey,
        if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN,
      )
    }
    callCallBacks()
  }

  /**
   * Returns the square the expected move starts from, or `null` when there is no expected move or
   * it was already answered. Grades the card [ReviewGrade.AGAIN] unless it already failed.
   */
  suspend fun hint(): BoardLocation? = assist()?.let { (from, _) -> from }

  /**
   * Returns the expected move's origin and destination squares, or `null` when there is none or it
   * was already answered. Grades the card [ReviewGrade.AGAIN] unless it already failed.
   */
  suspend fun reveal(): Pair<BoardLocation, BoardLocation>? = assist()

  private suspend fun assist(): Pair<BoardLocation, BoardLocation>? {
    if (answered) return null
    val edge = expectedEdge() ?: return null
    if (answered) return null // a move landed while the scope lookup suspended
    val start = GameEngine(node.positionKey)
    val squares =
      Pair(
        start.originSquare(edge.move),
        BoardUtils.destinationSquare(edge.move, start.playerTurn),
      )
    if (!failed) {
      failed = true
      trainingScheduler.grade(node.positionKey, ReviewGrade.AGAIN)
    }
    return squares
  }

  /** Puts the trained position back on the board for another attempt, keeping the card failed. */
  fun retry() {
    engine = GameEngine(node.positionKey)
    answered = false
    unblock()
    callCallBacks()
  }

  private suspend fun expectedEdge(): Edge? =
    node.outgoing.values.firstOrNull { it.isGood == true && isInScope(it) }

  private suspend fun isInScope(edge: Edge) =
    repertoireScope == null || repertoireScope in tagStore.tagsFor(edge.from, edge.to)

  /** Swaps the position the trainer drills. No op when the new node points at the same key. */
  fun updateNode(newNode: Node) {
    if (newNode.positionKey != node.positionKey || newNode.positionKey != engine.toPositionKey()) {
      node = newNode
      engine = GameEngine(node.positionKey)
      failed = false
      answered = false
      unblock()
      callCallBacks()
    }
  }
}
