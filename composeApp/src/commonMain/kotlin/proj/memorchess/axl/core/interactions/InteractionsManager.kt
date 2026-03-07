package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.BoardUtils
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.ui.components.popup.ToastRenderer

/**
 * Class that handles clicks on the chess board.
 *
 * @param engine The game engine managing the current position.
 * @constructor Creates an interaction manager from a game engine.
 */
abstract class InteractionsManager(var engine: GameEngine) : KoinComponent {

  val toastRenderer: ToastRenderer by inject()

  /** Coordinates of the tile that was clicked first. */
  var selectedTile by mutableStateOf<Pair<Int, Int>?>(null)
    private set

  private var isBlocked = false

  val needPromotion = mutableStateOf(false)

  private var pendingPromotionFrom: Pair<Int, Int>? = null
  private var pendingPromotionTo: Pair<Int, Int>? = null

  private val callBacks = mutableStateListOf<(Boolean) -> Unit>()

  /** Register a new callback that will be triggered on each tile change. */
  fun registerCallBack(callBack: (Boolean) -> Unit) {
    callBacks.add(callBack)
  }

  internal fun callCallBacks(withAnimation: Boolean = true) {
    callBacks.forEach { it(withAnimation) }
  }

  /**
   * Clicks on a tile.
   *
   * @param coordinates The clicked tile's coordinates.
   */
  suspend fun clickOnTile(coordinates: Pair<Int, Int>) {
    if (isBlocked) {
      LOGGER.w { "Clicked on tile ${BoardUtils.tileName(coordinates)} but the game is blocked" }
      return
    }
    LOGGER.i { "Clicked on tile ${BoardUtils.tileName(coordinates)}" }
    val immutableFirstTile = selectedTile
    if (immutableFirstTile != null) {
      try {
        if (engine.isPromotionMove(immutableFirstTile, coordinates)) {
          pendingPromotionFrom = immutableFirstTile
          pendingPromotionTo = coordinates
          needPromotion.value = true
        } else {
          val move = engine.playCoordinateMove(immutableFirstTile, coordinates)
          afterPlayMove(move)
        }
      } catch (e: IllegalMoveException) {
        if (getPlayerAt(coordinates) == getPlayerAt(immutableFirstTile)) {
          selectedTile = coordinates
          return
        }
      }
      selectedTile = null
    } else if (getPlayerAt(coordinates) == engine.playerTurn) {
      selectedTile = coordinates
    }
  }

  /**
   * Gets the player of the piece on the given coordinates.
   *
   * @param coordinates The coordinates of the tile to check.
   * @return The player of the piece on the tile, or null if there is no piece.
   */
  private fun getPlayerAt(coordinates: Pair<Int, Int>) =
    engine.pieceAt(coordinates.first, coordinates.second)?.player

  /**
   * Plays a move directly.
   *
   * @param move the move to play
   */
  suspend fun playMove(move: String) {
    engine.playSanMove(move)
    afterPlayMove(move)
  }

  /**
   * Applies a promotion.
   *
   * @param pieceKind The chosen piece kind to promote to.
   */
  suspend fun applyPromotion(pieceKind: PieceKind) {
    val from = checkNotNull(pendingPromotionFrom) { "No pending promotion" }
    val to = checkNotNull(pendingPromotionTo) { "No pending promotion" }
    val san = engine.playCoordinateMoveWithPromotion(from, to, pieceKind)
    needPromotion.value = false
    pendingPromotionFrom = null
    pendingPromotionTo = null
    afterPlayMove(san)
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
  fun reset(position: PositionKey? = null) {
    engine = if (position == null) GameEngine() else GameEngine(position)
    needPromotion.value = false
    selectedTile = null
    callCallBacks(false)
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

private val LOGGER = co.touchlab.kermit.Logger.withTag("CommandsManager")
