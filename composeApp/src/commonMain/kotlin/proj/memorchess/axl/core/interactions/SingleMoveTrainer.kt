package proj.memorchess.axl.core.interactions

import org.koin.core.component.inject
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.Edge
import proj.memorchess.axl.core.graph.Node
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.scheduling.ReviewGrade

/**
 * Trainer that drills a single position by asking the user to play one of its good outgoing moves.
 *
 * @property node The position being trained.
 * @property callBackOnCorrect Called after each move with the [Edge] for a correct move, or `null`
 *   for an incorrect or unknown move.
 */
class SingleMoveTrainer(private var node: Node, private val callBackOnCorrect: (Edge?) -> Unit) :
  InteractionsManager(GameEngine(node.positionKey)) {

  private val trainingScheduler: TrainingScheduler by inject()

  private var isCorrect: Boolean = true

  override suspend fun afterPlayMove(move: String) {
    val matchingEdge = node.outgoing.values.firstOrNull { it.move == move }
    isCorrect = matchingEdge != null && matchingEdge.isGood == true
    block()
    callBackOnCorrect(if (isCorrect) matchingEdge else null)
    trainingScheduler.grade(
      node.positionKey,
      if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN,
    )
    callCallBacks()
  }

  /** Swaps the position the trainer drills. No op when the new node points at the same key. */
  fun updateNode(newNode: Node) {
    if (newNode.positionKey != node.positionKey || newNode.positionKey != engine.toPositionKey()) {
      node = newNode
      engine = GameEngine(node.positionKey)
      isCorrect = true
      unblock()
      callCallBacks()
    }
  }
}
