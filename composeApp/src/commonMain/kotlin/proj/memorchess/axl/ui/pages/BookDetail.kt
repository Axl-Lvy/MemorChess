package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
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
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.components.popup.ToastRenderer

/**
 * Book detail page showing book moves and allowing users to browse/download them.
 *
 * @param bookId The ID of the book to display.
 * @param editing Whether the user can edit the book.
 */
@Composable
fun BookDetail(
  bookId: Long,
  editing: Boolean = false,
  bookQueryManager: SupabaseBookQueryManager = koinInject(),
  nodeManager: NodeManager = koinInject(named("book")) { parametersOf(bookId) },
  authManager: AuthManager = koinInject(),
  toastRenderer: ToastRenderer = koinInject(),
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
      loadBookData(
        bookId = bookId,
        editing = editing,
        bookQueryManager = bookQueryManager,
        nodeManager = nodeManager,
        authManager = authManager,
        toastRenderer = toastRenderer,
        onBookLoaded = { loadedBook, explorer ->
          book = loadedBook
          bookExplorer = explorer
        },
      )
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

/** Loads book data and initializes the explorer. */
private suspend fun loadBookData(
  bookId: Long,
  editing: Boolean,
  bookQueryManager: SupabaseBookQueryManager,
  nodeManager: NodeManager,
  authManager: AuthManager,
  toastRenderer: ToastRenderer,
  onBookLoaded: (Book, BookExplorer) -> Unit,
) {
  try {
    val fetchedBook = bookQueryManager.getBook(bookId) ?: return
    val canEdit = editing && authManager.hasUserPermission(UserPermission.BOOK_CREATION)

    if (!canEdit && editing) {
      toastRenderer.info("You do not have permission to edit this book.")
    }

    nodeManager.resetCacheFromSource()
    val explorer = BookExplorer(fetchedBook, canEdit, nodeManager, nodeManager.treeRepository)
    onBookLoaded(fetchedBook, explorer)
  } catch (e: Exception) {
    LOGGER.e(e) { "Failed to load book $bookId" }
    toastRenderer.info("Failed to load book.")
  }
}

@Composable
private fun BookDetailContent(book: Book, explorer: BookExplorer) {
  val coroutineScope = rememberCoroutineScope()

  val deletionConfirmationDialog = remember { ConfirmationDialog(okText = "Delete") }
  deletionConfirmationDialog.DrawDialog()

  ExplorerContent(
    explorer = explorer,
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
      } else {
        Button(
          modifier = Modifier.fillMaxWidth(),
          onClick = { coroutineScope.launch { explorer.downloadBookToRepertoire() } },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
          Icon(FeatherIcons.Download, contentDescription = "Download to Repertoire")
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
    header = {
      Text(
        text = "Book: ${book.name}",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(8.dp),
      )
    },
  )
}

private val LOGGER = Logger.withTag("BookDetail")
