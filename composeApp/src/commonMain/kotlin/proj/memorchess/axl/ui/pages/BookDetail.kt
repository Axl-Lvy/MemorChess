package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout

/**
 * Book detail page showing book moves and allowing users to browse/download them.
 *
 * @param bookId The ID of the book to display.
 */
@Composable
fun BookDetail(bookId: Long, bookQueryManager: BookQueryManager = koinInject()) {
  var book by remember { mutableStateOf<Book?>(null) }
  var bookExplorer by remember { mutableStateOf<BookExplorer?>(null) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag("book_detail_$bookId"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LoadingWidget({
      val books = bookQueryManager.getAllBooks()
      book = books.find { it.id == bookId }
      book?.let {
        val explorer = BookExplorer(it)
        explorer.loadBookMoves()
        bookExplorer = explorer
      }
    }) {
      val currentBook = book
      val explorer = bookExplorer

      if (currentBook == null || explorer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Book not found", style = MaterialTheme.typography.bodyLarge)
        }
      } else {
        BookDetailContent(book = currentBook, explorer = explorer)
      }
    }
  }
}

@Composable
private fun BookDetailContent(book: Book, explorer: BookExplorer) {
  val modifier = Modifier.fillMaxWidth()
  var inverted by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val nextMoves = remember { mutableStateListOf(*explorer.getNextMoves().toTypedArray()) }

  remember {
    explorer.registerCallBack {
      nextMoves.clear()
      nextMoves.addAll(explorer.getNextMoves())
    }
  }

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
      stateIndicators = { /* Not used for book exploration */ },
      saveButton = { /* Not used for book exploration */ },
      deleteButton = {
        Button(
          onClick = { coroutineScope.launch { explorer.downloadBookToRepertoire() } },
          it,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
          Icon(FeatherIcons.Download, contentDescription = "Download to Repertoire")
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
