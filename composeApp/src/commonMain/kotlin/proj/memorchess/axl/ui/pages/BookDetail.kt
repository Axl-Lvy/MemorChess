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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ToastRenderer

/**
 * Book detail page showing book moves and allowing users to browse and download them.
 *
 * Books are read-only for clients — there is no editing functionality.
 *
 * @param bookId The ID of the book to display.
 */
@Composable
fun BookDetail(
  bookId: Long,
  bookQueryManager: BookQueryManager = koinInject(),
  nodeManager: NodeManager = koinInject(named("book")) { parametersOf(bookId) },
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
        bookQueryManager = bookQueryManager,
        nodeManager = nodeManager,
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
  bookQueryManager: BookQueryManager,
  nodeManager: NodeManager,
  toastRenderer: ToastRenderer,
  onBookLoaded: (Book, BookExplorer) -> Unit,
) {
  try {
    val fetchedBook = bookQueryManager.getBook(bookId) ?: return
    nodeManager.resetCacheFromSource()
    val explorer = BookExplorer(fetchedBook, nodeManager)
    onBookLoaded(fetchedBook, explorer)
  } catch (e: Exception) {
    LOGGER.e(e) { "Failed to load book $bookId" }
    toastRenderer.info("Failed to load book.")
  }
}

@Composable
private fun BookDetailContent(book: Book, explorer: BookExplorer) {
  val coroutineScope = rememberCoroutineScope()

  ExplorerContent(
    explorer = explorer,
    saveButton = {
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { coroutineScope.launch { explorer.downloadBookToRepertoire() } },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
      ) {
        Icon(FeatherIcons.Download, contentDescription = "Download to Repertoire")
      }
    },
    deleteButton = {},
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
