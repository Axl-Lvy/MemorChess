package proj.memorchess.axl.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route

/** The number of books to fetch per batch when loading more books. */
private const val BOOKS_PER_BATCH = 50

/** The number of items before the end of the list at which to trigger loading more books. */
private const val LOAD_MORE_THRESHOLD = 25

/**
 * State holder for the Books screen.
 *
 * Manages all state related to book listing, pagination, loading, and user interactions. Includes
 * thread-safety mechanisms to prevent race conditions during concurrent operations.
 */
private class BooksState {
  /** The list of currently loaded books. */
  var books by mutableStateOf<List<Book>>(emptyList())

  /** Whether a pull-to-refresh operation is in progress. */
  var isRefreshing by mutableStateOf(false)

  /** Whether books are currently being loaded from the next batch. */
  var isLoadingMore by mutableStateOf(false)

  /** Whether there are more books available to load from the server. */
  var hasMoreBooks by mutableStateOf(true)

  /** The current offset for pagination (number of books already loaded). */
  var currentOffset by mutableStateOf(0L)

  /** The current filter text for searching books by name. */
  var filterText by mutableStateOf("")

  /** Lock to prevent concurrent load/refresh operations. */
  private var isOperationInProgress by mutableStateOf(false)

  /**
   * Attempts to acquire the operation lock.
   *
   * This prevents race conditions when multiple operations (like rapid refresh attempts) try to
   * execute concurrently.
   *
   * @return true if the lock was successfully acquired, false if an operation is already in
   *   progress.
   */
  fun canStartOperation(): Boolean {
    if (isOperationInProgress) return false
    isOperationInProgress = true
    return true
  }

  /**
   * Releases the operation lock.
   *
   * Should be called in a finally block to ensure the lock is always released.
   */
  fun operationComplete() {
    isOperationInProgress = false
  }

  /**
   * Resets all pagination-related state to initial values.
   *
   * This is called when refreshing the entire book list from the beginning.
   */
  fun resetPagination() {
    currentOffset = 0
    hasMoreBooks = true
    books = emptyList()
    isLoadingMore = false
    isOperationInProgress = false
  }

  /**
   * Updates the book list with newly loaded books.
   *
   * Filters out any duplicate books (by ID) that may have been loaded due to race conditions, then
   * updates the pagination state accordingly.
   *
   * @param newBooks The list of books fetched from the server.
   */
  fun updateBooksAfterLoad(newBooks: List<Book>) {
    val existingIds = books.map { it.id }.toSet()
    val uniqueNewBooks = newBooks.filter { it.id !in existingIds }
    books = books + uniqueNewBooks
    currentOffset += uniqueNewBooks.size
    hasMoreBooks = newBooks.size == BOOKS_PER_BATCH
  }
}

/**
 * Callback functions for book content interactions.
 *
 * Groups all user interaction callbacks for the books content area to reduce parameter count.
 *
 * @property onRefresh Called when the user triggers a pull-to-refresh.
 * @property onBookClick Called when a user clicks on a book.
 * @property onLoadMore Called when more books should be loaded (pagination trigger).
 */
private data class BooksContentCallbacks(
  val onRefresh: () -> Unit,
  val onBookClick: (Book) -> Unit,
  val onLoadMore: () -> Unit,
)

/**
 * Books page that displays available books with lazy loading pagination.
 *
 * This page provides:
 * - A scrollable list of books with automatic pagination (batches of [BOOKS_PER_BATCH])
 * - Pull-to-refresh functionality to reload the book list
 * - Navigation to book detail pages
 *
 * The implementation includes race condition prevention to handle rapid user interactions and
 * ensures no duplicate books appear in the list.
 *
 * @param bookQueryManager Manager for book-related queries. Injected via Koin.
 * @param navigator Navigation controller for page transitions. Injected via Koin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Books(
  bookQueryManager: BookQueryManager = koinInject(),
  navigator: Navigator = koinInject(),
  toastRenderer: ToastRenderer = koinInject(),
) {
  val state = remember { BooksState() }
  val coroutineScope = rememberCoroutineScope()

  /**
   * Loads the next batch of books from the server.
   *
   * This function:
   * - Checks if a load operation is already in progress or if all books have been loaded
   * - Acquires an operation lock to prevent concurrent executions
   * - Fetches [BOOKS_PER_BATCH] books starting from the current offset
   * - Updates the state with new books (filtering duplicates)
   * - Determines if more books are available based on the batch size
   *
   * Called automatically when the user scrolls near the end of the list.
   */
  suspend fun loadMoreBooks() {
    if (state.isLoadingMore || !state.hasMoreBooks || !state.canStartOperation()) return
    state.isLoadingMore = true
    try {
      val newBooks =
        bookQueryManager.getAllBooks(
          offset = state.currentOffset,
          limit = BOOKS_PER_BATCH,
          text = state.filterText,
        )
      if (newBooks.isNotEmpty()) {
        state.updateBooksAfterLoad(newBooks)
      } else {
        state.hasMoreBooks = false
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Failed to fetch books." }
      toastRenderer.info("Failed to load books.")
    } finally {
      state.isLoadingMore = false
      state.operationComplete()
    }
  }

  /**
   * Refreshes the entire books list from the beginning.
   *
   * This function:
   * - Acquires an operation lock to prevent concurrent refresh operations
   * - Resets all pagination state (offset, hasMoreBooks, etc.)
   * - Clears the current book list
   * - Loads the first batch of books
   *
   * Called when the page is first loaded or when the user triggers pull-to-refresh.
   */
  suspend fun refreshBooksList() {
    if (!state.canStartOperation()) return
    try {
      state.resetPagination()
      loadMoreBooks()
    } finally {
      state.operationComplete()
    }
  }

  LoadingWidget({ refreshBooksList() }) {
    Column(
      modifier =
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp).testTag("books"),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing) {
          refreshBooksList()
          state.isRefreshing = false
        }
      }

      BooksHeader(
        filterText = state.filterText,
        onFilterTextChange = { newText ->
          state.filterText = newText
          state.isRefreshing = true
        },
      )

      BooksContent(
        books = state.books,
        isRefreshing = state.isRefreshing,
        isLoadingMore = state.isLoadingMore,
        hasMoreBooks = state.hasMoreBooks,
        callbacks =
          BooksContentCallbacks(
            onRefresh = { state.isRefreshing = true },
            onBookClick = { book -> navigator.navigateTo(Route.BookDetailRoute(book.id)) },
            onLoadMore = { coroutineScope.launch { loadMoreBooks() } },
          ),
      )
    }
  }
}

/**
 * Header section for the Books page.
 *
 * Displays the page title "Books" and a search icon to toggle the filter field. The filter field
 * appears below the header with a smooth animation when toggled.
 *
 * @param filterText The current filter text for searching books.
 * @param onFilterTextChange Callback invoked when the filter text changes.
 */
@Composable
private fun BooksHeader(filterText: String, onFilterTextChange: (String) -> Unit) {
  var showFilterField by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Books", style = MaterialTheme.typography.headlineMedium)
      IconButton(onClick = { showFilterField = !showFilterField }) {
        Icon(Icons.Default.Search, contentDescription = "Filter books")
      }
    }

    AnimatedVisibility(
      visible = showFilterField,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      OutlinedTextField(
        value = filterText,
        onValueChange = onFilterTextChange,
        label = { Text("Filter by name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      )
    }
  }
}

/**
 * Content area for the Books page.
 *
 * Displays either an empty state message when no books are available, or a scrollable list of books
 * with pull-to-refresh functionality.
 *
 * @param books The list of books to display.
 * @param isRefreshing Whether a refresh operation is currently in progress.
 * @param isLoadingMore Whether more books are currently being loaded.
 * @param hasMoreBooks Whether there are more books available to load.
 * @param callbacks Collection of callback functions for user interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BooksContent(
  books: List<Book>,
  isRefreshing: Boolean,
  isLoadingMore: Boolean,
  hasMoreBooks: Boolean,
  callbacks: BooksContentCallbacks,
) {
  if (books.isEmpty()) {
    EmptyBooksMessage()
  } else {
    PullToRefreshBox(isRefreshing, callbacks.onRefresh) {
      BooksList(
        books = books,
        isLoadingMore = isLoadingMore,
        hasMoreBooks = hasMoreBooks,
        onBookClick = callbacks.onBookClick,
        onLoadMore = callbacks.onLoadMore,
      )
    }
  }
}

/**
 * Empty state message displayed when no books are available.
 *
 * Shows a centered message informing the user that there are no books to display.
 */
@Composable
private fun EmptyBooksMessage() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text("No books available", style = MaterialTheme.typography.bodyLarge)
  }
}

/**
 * Scrollable list of books with pagination support.
 *
 * Displays books in a lazy column, automatically triggering pagination when the user scrolls near
 * the end ([LOAD_MORE_THRESHOLD] items before the bottom). Shows a loading indicator while more
 * books are being fetched.
 *
 * @param books The list of books to display.
 * @param isLoadingMore Whether more books are currently being loaded.
 * @param hasMoreBooks Whether there are more books available to load.
 * @param onBookClick Callback invoked when a book is clicked.
 * @param onLoadMore Callback to trigger loading more books.
 */
@Composable
private fun BooksList(
  books: List<Book>,
  isLoadingMore: Boolean,
  hasMoreBooks: Boolean,
  onBookClick: (Book) -> Unit,
  onLoadMore: () -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(books.size, key = { index -> "${books[index].id}-$index" }) { index ->
      val book = books[index]
      BookListItem(book = book, onClick = { onBookClick(book) })

      if (shouldLoadMore(index, books.size, hasMoreBooks, isLoadingMore)) {
        LaunchedEffect(Unit) { onLoadMore() }
      }
    }

    if (isLoadingMore) {
      item { LoadingMoreIndicator() }
    }
  }
}

/**
 * Determines whether more books should be loaded based on the current scroll position.
 *
 * Triggers loading when the user has scrolled to within [LOAD_MORE_THRESHOLD] items of the end of
 * the list, and only if:
 * - More books are available ([hasMoreBooks] is true)
 * - A load operation is not already in progress ([isLoadingMore] is false)
 *
 * @param currentIndex The index of the current item being rendered.
 * @param totalBooks The total number of books currently loaded.
 * @param hasMoreBooks Whether more books are available to load.
 * @param isLoadingMore Whether a load operation is currently in progress.
 * @return true if more books should be loaded, false otherwise.
 */
private fun shouldLoadMore(
  currentIndex: Int,
  totalBooks: Int,
  hasMoreBooks: Boolean,
  isLoadingMore: Boolean,
): Boolean {
  return currentIndex >= totalBooks - LOAD_MORE_THRESHOLD && hasMoreBooks && !isLoadingMore
}

/**
 * Loading indicator displayed at the bottom of the books list.
 *
 * Shows a "Loading more..." message centered at the bottom of the list while additional books are
 * being fetched from the server.
 */
@Composable
private fun LoadingMoreIndicator() {
  Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
    Text("Loading more...", style = MaterialTheme.typography.bodyMedium)
  }
}

/**
 * Individual book item in the list.
 *
 * Displays a card with the book's name and download count.
 *
 * @param book The book to display.
 * @param onClick Callback invoked when the book card is clicked.
 */
@Composable
private fun BookListItem(book: Book, onClick: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(book.name, style = MaterialTheme.typography.titleMedium)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          FeatherIcons.Download,
          contentDescription = "Downloads",
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(book.downloads.toString(), style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

private val LOGGER = Logger.withTag("Books")
