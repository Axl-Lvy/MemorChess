package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.UserPermission
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.graph.nodes.IsolatedBookNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout

/**
 * Book detail page showing book moves and allowing users to browse/download them.
 *
 * @param bookId The ID of the book to display.
 */
@Composable
fun BookDetail(
  bookId: Long,
  bookQueryManager: SupabaseBookQueryManager = koinInject(),
  authManager: AuthManager = koinInject(),
  nodeManager: NodeManager<IsolatedBookNode> = koinInject(named("book")) { parametersOf(bookId) },
) {

  var book by remember(bookId) { mutableStateOf<Book?>(null) }
  var bookExplorer by remember(bookId) { mutableStateOf<BookExplorer?>(null) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag("book_detail_${bookId}"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LoadingWidget({
      val fetchedBook = bookQueryManager.getBook(bookId)
      if (fetchedBook != null) {
        book = fetchedBook
        nodeManager.resetCacheFromSource()
        val explorer =
          BookExplorer(
            fetchedBook,
            authManager.hasUserPermission(UserPermission.BOOK_CREATION),
            nodeManager,
          )
        bookExplorer = explorer
      }
    }) {
      val immutableBook = book
      val immutableBookExplorer = bookExplorer

      if (immutableBook == null || immutableBookExplorer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Book not found", style = MaterialTheme.typography.bodyLarge)
        }
      } else {
        BookDetailContent(immutableBook, explorer = immutableBookExplorer)
      }
    }
  }
}

@Composable
private fun BookDetailContent(book: Book, explorer: BookExplorer) {
  val modifier = Modifier.fillMaxWidth()
  var inverted by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val nextMoves = mutableStateListOf(*explorer.getNextMoves().toTypedArray())

  remember {
    explorer.registerCallBack {
      nextMoves.clear()
      nextMoves.addAll(explorer.getNextMoves())
    }
  }

  val deletionConfirmationDialog = remember { ConfirmationDialog(okText = "Delete") }
  deletionConfirmationDialog.DrawDialog()

  val content = remember {
    ExploreLayoutContent(
      resetButton = { ControlButton.RESET.render(it) { explorer.reset() } },
      reverseButton = { ControlButton.REVERSE.render(it) { inverted = !inverted } },
      backButton = { ControlButton.BACK.render(it) { explorer.back() } },
      forwardButton = { /* Not used for book exploration */ },
      nextMoveButtons = {
        nextMoves.map<String, @Composable (() -> Unit)> {
          { NextMoveButton(it) { coroutineScope.launch { explorer.playMove(it) } } }
        }
      },
      playerTurnIndicator = {
        var playerTurn by remember {
          mutableStateOf(explorer.game.position.playerTurn == Game.Player.WHITE)
        }
        explorer.registerCallBack {
          playerTurn = explorer.game.position.playerTurn == Game.Player.WHITE
        }
        Piece(if (playerTurn) King.white() else King.black())
      },
      stateIndicators = {
        Button(
          onClick = { coroutineScope.launch { explorer.downloadBookToRepertoire() } },
          it,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
          Icon(FeatherIcons.Download, contentDescription = "Download to Repertoire")
        }
      },
      saveButton = {
        if (explorer.canEdit) {
          Button(
            onClick = { coroutineScope.launch { explorer.save() } },
            it,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          ) {
            Icon(
              FeatherIcons.Save,
              contentDescription = "Save Move",
              tint = MaterialTheme.colorScheme.onPrimary,
            )
          }
        }
      },
      deleteButton = {
        if (explorer.canEdit) {
          Button(
            onClick = {
              deletionConfirmationDialog.show(
                confirm = {
                  coroutineScope.launch {
                    explorer.delete()
                    explorer.callCallBacks()
                  }
                }
              ) {
                Text(
                  "Are you sure you want to delete ${explorer.calculateNumberOfNodeToDelete()} nodes from book ${book.name}?"
                )
              }
            },
            it,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Icon(FeatherIcons.Trash, contentDescription = "Delete Book")
          }
        }
      },
      board = { Board(inverted, explorer, it) },
    )
  }

  Column {
    Text(
      book.name,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(bottom = 8.dp),
    )

    BoxWithConstraints(modifier = Modifier.weight(1f)) {
      if (maxHeight > maxWidth) PortraitExploreLayout(modifier, content)
      else LandscapeExploreLayout(modifier, content)
    }
  }
}

@Composable
private fun NextMoveButton(move: String, playMove: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().clickable { playMove() },
    contentAlignment = Alignment.Center,
  ) {
    Text(move)
  }
}
