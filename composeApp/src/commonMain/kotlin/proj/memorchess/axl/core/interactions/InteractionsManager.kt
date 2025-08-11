package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.diamondedge.logging.logging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.moves.IllegalMoveException
import proj.memorchess.axl.core.engine.moves.description.MoveDescription
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.popup.info

/**
 * Class that handles clicks on the chess board.
 *
 * @param game The game that is being played.
 * @constructor Creates an interaction manager from a game.
 */
abstract class InteractionsManager(var game: Game) : KoinComponent {

  val nodeManager: NodeManager by inject()

  /** Coordinates of the tile that was clicked first. */
  private var firstTile: Pair<Int, Int>? = null

  private var isBlocked = false

  val needPromotion = mutableStateOf(false)

  private var moveBeforePromotion: String? = null

  private val callBacks = mutableStateListOf<() -> Unit>()

  /** Register a new callback that will be triggered on each tile change. */
  fun registerCallBack(callBack: () -> Unit) {
    callBacks.add(callBack)
  }

  internal fun callCallBacks() {
    callBacks.forEach { it() }
  }

  /**
   * Clicks on a tile.
   *
   * @param coordinates The clicked tile's coordinates.
   */
  suspend fun clickOnTile(coordinates: Pair<Int, Int>) {
    if (isBlocked) {
      LOGGER.warn { "Clicked on tile ${IBoard.getTileName(coordinates)} but the game is blocked" }
      return
    }
    LOGGER.info { "Clicked on tile ${IBoard.getTileName(coordinates)}" }
    val immutableFirstTile = firstTile
    if (immutableFirstTile != null) {
      try {
        val move = game.playMove(MoveDescription(immutableFirstTile, coordinates))
        needPromotion.value = game.needPromotion()
        if (game.needPromotion()) {
          moveBeforePromotion = move
        } else {
          afterPlayMove(move)
        }
      } catch (e: IllegalMoveException) {
        info(e.message.toString())
      }
      firstTile = null
    } else if (
      game.position.board.getTile(coordinates).getSafePiece()?.player == game.position.playerTurn
    ) {
      firstTile = coordinates
    }
  }

  /**
   * Plays a move directly.
   *
   * @param move the move to play
   */
  suspend fun playMove(move: String) {
    game.playMove(move)
    needPromotion.value = game.needPromotion()
    if (game.needPromotion()) {
      moveBeforePromotion = move
    } else {
      afterPlayMove(move)
    }
  }

  /**
   * Applies a promotion
   *
   * @param newPieceName The chosen piece name
   */
  suspend fun applyPromotion(newPieceName: String) {
    game.applyPromotion(newPieceName)
    needPromotion.value = game.needPromotion()
    afterPlayMove(moveBeforePromotion!! + "=$newPieceName")
    moveBeforePromotion = null
  }

  /**
   * Called after a move is played.
   *
   * @param move The move that was played.
   */
  abstract suspend fun afterPlayMove(move: String)

  /**
   * Resets the game to a new position.
   *
   * @param position The new position key to reset the game to.
   */
  fun reset(position: PositionIdentifier) {
    game = Game(position)
    needPromotion.value = game.needPromotion()
    firstTile = null
    callCallBacks()
  }

  /** Blocks this object. No move can be played. */
  fun block() {
    isBlocked = true
  }

  /** Unblocks this object. Moves can be played. */
  fun unblock() {
    isBlocked = false
  }
}

private val LOGGER = logging()
