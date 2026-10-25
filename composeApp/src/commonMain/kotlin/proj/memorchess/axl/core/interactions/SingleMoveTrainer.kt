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
 * Trainer that drills a single position by asking the user to play one of its good outgoing moves.
 *
 * @property node The position being trained.
 * @property repertoireScope When not `null`, a move is only accepted when it is also tagged with
 *   this repertoire, even when it is otherwise a correct, classified move. `null` behaves exactly
 *   as before this parameter existed.
 * @property callBackOnCorrect Called after each move with the [Edge] for a correct move (or `null`
 *   for an incorrect or unknown move), the square the move was played to, and, only for a wrong
 *   attempt with an in-scope good edge, the square that move should have landed on.
 */
class SingleMoveTrainer(
  private var node: Node,
  private val repertoireScope: String? = null,
  private val callBackOnCorrect: (Edge?, BoardLocation, BoardLocation?) -> Unit,
) : InteractionsManager(GameEngine(node.positionKey)) {

  private val trainingScheduler: TrainingScheduler by inject()
  private val treeStore: TreeStore by inject()
  private val tagStore: RepertoireTagStore by inject()

  private var isCorrect: Boolean = true

  // Set once the card is graded by a hint or reveal, so the replayed move is not graded again.
  private var assisted = false
  private var answered = false

  override suspend fun afterPlayMove(move: String) {
    answered = true
    val matchingEdge = node.outgoing.values.firstOrNull { it.move == move }
    val inScope = repertoireScope == null || (matchingEdge != null && isInScope(matchingEdge))
    isCorrect = matchingEdge != null && matchingEdge.isGood == true && inScope

    val moverWasWhite = engine.playerTurn == Player.BLACK // engine already advanced past this move
    val playedSquare = BoardUtils.destinationSquare(move, moverWasWhite)
    val correctSquare =
      if (isCorrect) null
      else expectedEdge()?.let { BoardUtils.destinationSquare(it.move, moverWasWhite) }

    block()
    callBackOnCorrect(if (isCorrect) matchingEdge else null, playedSquare, correctSquare)
    if (!assisted) {
      trainingScheduler.grade(
        node.positionKey,
        if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN,
      )
    }
    callCallBacks()
  }

  /**
   * Returns the square the expected move starts from, or `null` when there is no expected move or
   * it was already answered. Grades the card [ReviewGrade.AGAIN] unless a hint or reveal already
   * did.
   */
  suspend fun hint(): BoardLocation? = assist()?.let { (from, _) -> from }

  /**
   * Returns the origin and destination squares of the expected move, or `null` when there is no
   * expected move or it was already answered. Grades the card [ReviewGrade.AGAIN] unless a hint or
   * reveal already did.
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
        BoardUtils.destinationSquare(edge.move, start.playerTurn == Player.WHITE),
      )
    if (!assisted) {
      assisted = true
      trainingScheduler.grade(node.positionKey, ReviewGrade.AGAIN)
    }
    return squares
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
      isCorrect = true
      assisted = false
      answered = false
      unblock()
      callCallBacks()
    }
  }
}
