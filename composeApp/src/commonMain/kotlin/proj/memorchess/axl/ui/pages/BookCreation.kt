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
import compose.icons.feathericons.ThumbsDown
import compose.icons.feathericons.ThumbsUp
import compose.icons.feathericons.Trash
import compose.icons.feathericons.Trash2
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.interactions.BookCreationExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route

/**
 * Book creation page for users with BOOK_CREATION permission.
 *
 * @param bookId The ID of the book to edit. If null, a new book creation dialog will be shown.
 */
@Composable
fun BookCreation(
  bookId: Long?,
  bookQueryManager: BookQueryManager = koinInject(),
  navigator: Navigator = koinInject(),
) {
  var book by remember { mutableStateOf<Book?>(null) }
  var bookCreationExplorer by remember { mutableStateOf<BookCreationExplorer?>(null) }

  Column(
    modifier =
      Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 8.dp).testTag("book_creation"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LoadingWidget({
      if (bookId != null) {
        val books = bookQueryManager.getAllBooks()
        book = books.find { it.id == bookId }
        book?.let {
          val explorer = BookCreationExplorer(it)
          explorer.loadBook(it)
          bookCreationExplorer = explorer
        }
      } else {
        bookCreationExplorer = BookCreationExplorer()
      }
    }) {
      val explorer = bookCreationExplorer

      if (explorer == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Failed to initialize book creation", style = MaterialTheme.typography.bodyLarge)
        }
      } else {
        BookCreationContent(explorer = explorer, navigator = navigator)
      }
    }
  }
}

@Composable
private fun BookCreationContent(explorer: BookCreationExplorer, navigator: Navigator) {
  val modifier = Modifier.fillMaxWidth()
  var inverted by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val nextMoves = remember { mutableStateListOf(*explorer.getNextMoves().toTypedArray()) }
  var showCreateDialog by remember { mutableStateOf(explorer.getCurrentBook() == null) }

  remember {
    explorer.registerCallBack {
      nextMoves.clear()
      nextMoves.addAll(explorer.getNextMoves())
    }
  }

  val deletionConfirmationDialog = remember { ConfirmationDialog(okText = "Delete") }
  deletionConfirmationDialog.DrawDialog()

  val moveDeleteConfirmationDialog = remember { ConfirmationDialog(okText = "Delete") }
  moveDeleteConfirmationDialog.DrawDialog()

  val content = remember {
    ExploreLayoutContent(
      resetButton = { ControlButton.RESET.render(it) { explorer.reset() } },
      reverseButton = { ControlButton.REVERSE.render(it) { inverted = !inverted } },
      backButton = { ControlButton.BACK.render(it) { explorer.back() } },
      forwardButton = { /* Not used for book creation */ },
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
      stateIndicators = { /* Book creation indicator could go here */ },
      saveButton = {
        Row(modifier = it) {
          IconButton(onClick = { coroutineScope.launch { explorer.saveCurrentMoveAsGood() } }) {
            Icon(
              FeatherIcons.ThumbsUp,
              contentDescription = "Save as Good Move",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(onClick = { coroutineScope.launch { explorer.saveCurrentMoveAsBad() } }) {
            Icon(
              FeatherIcons.ThumbsDown,
              contentDescription = "Save as Bad Move",
              tint = MaterialTheme.colorScheme.error,
            )
          }
          IconButton(
            onClick = {
              moveDeleteConfirmationDialog.show(
                confirm = { coroutineScope.launch { explorer.deleteCurrentMove() } }
              ) {
                Text("Are you sure you want to delete this move from the book?")
              }
            }
          ) {
            Icon(
              FeatherIcons.Trash2,
              contentDescription = "Delete Move",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      deleteButton = {
        Button(
          onClick = {
            deletionConfirmationDialog.show(
              confirm = {
                coroutineScope.launch {
                  explorer.deleteCurrentBook()
                  navigator.navigateTo(Route.BooksRoute)
                }
              }
            ) {
              Text("Are you sure you want to delete this book?")
            }
          },
          it,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Icon(FeatherIcons.Trash, contentDescription = "Delete Book")
        }
      },
      board = { Board(inverted, explorer, it) },
    )
  }

  Column {
    val currentBook = explorer.getCurrentBook()
    if (currentBook != null) {
      Text(
        "Editing: ${currentBook.name}",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }

    BoxWithConstraints(modifier = Modifier.weight(1f)) {
      if (maxHeight > maxWidth) PortraitExploreLayout(modifier, content)
      else LandscapeExploreLayout(modifier, content)
    }
  }

  if (showCreateDialog) {
    CreateBookDialog(
      onDismiss = { navigator.navigateTo(Route.BooksRoute) },
      onCreate = { name ->
        coroutineScope.launch {
          explorer.createBook(name)
          showCreateDialog = false
        }
      },
    )
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

@Composable
private fun CreateBookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
  var bookName by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create New Book") },
    text = {
      OutlinedTextField(
        value = bookName,
        onValueChange = { bookName = it },
        label = { Text("Book Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(onClick = { onCreate(bookName) }, enabled = bookName.isNotBlank()) {
        Text("Create")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
