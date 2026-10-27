package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.toast_illegal_move
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.BoardUtils
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.ui.components.popup.ToastRenderer

/** Handles clicks and moves on the chess board. */
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

  /** Registers a callback triggered on each tile change. */
  fun registerCallBack(callBack: (Boolean) -> Unit) {
    callBacks.add(callBack)
  }

  internal fun callCallBacks(withAnimation: Boolean = true) {
    callBacks.forEach { it(withAnimation) }
  }

  /** Handles a click on the tile at [coordinates]. */
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

  private fun getPlayerAt(coordinates: Pair<Int, Int>) =
    engine.pieceAt(coordinates.first, coordinates.second)?.player

  /** Plays [move], given in SAN; an illegal move is rejected with a toast. */
  suspend fun playMove(move: String) {
    // External sources may add "+"/"#" or over-disambiguate; the engine's spelling keeps one edge.
    val san =
      try {
        engine.playSanMove(move)
      } catch (e: IllegalMoveException) {
        LOGGER.w(e) { "Rejected illegal move: $move" }
        toastRenderer.info(Res.string.toast_illegal_move)
        return
      }
    afterPlayMove(san)
  }

  /** Completes the pending promotion with [pieceKind]. */
  suspend fun applyPromotion(pieceKind: PieceKind) {
    val from = checkNotNull(pendingPromotionFrom) { "No pending promotion" }
    val to = checkNotNull(pendingPromotionTo) { "No pending promotion" }
    val san = engine.playCoordinateMoveWithPromotion(from, to, pieceKind)
    needPromotion.value = false
    pendingPromotionFrom = null
    pendingPromotionTo = null
    afterPlayMove(san)
  }

  /** Called after [move], in SAN, is played. */
  abstract suspend fun afterPlayMove(move: String)

  /** Resets the game to [position], or to the starting position when `null`. */
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
