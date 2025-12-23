package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.UserPermission
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
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

  /** Whether the current user has permission to create books. */
  var hasCreationPermission by mutableStateOf(false)

  /** Whether a pull-to-refresh operation is in progress. */
  var isRefreshing by mutableStateOf(false)

  /** Whether books are currently being loaded from the next batch. */
  var isLoadingMore by mutableStateOf(false)

  /** Whether there are more books available to load from the server. */
  var hasMoreBooks by mutableStateOf(true)

  /** The current offset for pagination (number of books already loaded). */
  var currentOffset by mutableStateOf(0L)

  /** Whether the create book dialog is currently shown. */
  var showCreateDialog by mutableStateOf(false)

  /** The book currently selected for actions (edit/delete), or null if no book is selected. */
  var selectedBookForActions by mutableStateOf<Book?>(null)

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
 * @property onBookLongClick Called when a user long-presses on a book (for edit/delete actions).
 * @property onLoadMore Called when more books should be loaded (pagination trigger).
 */
private data class BooksContentCallbacks(
  val onRefresh: () -> Unit,
  val onBookClick: (Book) -> Unit,
  val onBookLongClick: (Book) -> Unit,
  val onLoadMore: () -> Unit,
)

/**
 * Books page that displays available books with lazy loading pagination.
 *
 * This page provides:
 * - A scrollable list of books with automatic pagination (batches of [BOOKS_PER_BATCH])
 * - Pull-to-refresh functionality to reload the book list
 * - Book creation dialog for users with [UserPermission.BOOK_CREATION] permission
 * - Edit and delete actions for books (accessible via long press)
 * - Navigation to book detail pages
 *
 * The implementation includes race condition prevention to handle rapid user interactions and
 * ensures no duplicate books appear in the list.
 *
 * @param bookQueryManager Manager for book-related database operations. Injected via Koin.
 * @param navigator Navigation controller for page transitions. Injected via Koin.
 * @param authManager Authentication manager for checking user permissions. Injected via Koin.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Books(
  bookQueryManager: SupabaseBookQueryManager = koinInject(),
  navigator: Navigator = koinInject(),
  authManager: AuthManager = koinInject(),
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
   * - Checks if the user has book creation permission
   * - Loads the first batch of books
   *
   * Called when the page is first loaded or when the user triggers pull-to-refresh.
   */
  suspend fun refreshBooksList() {
    if (!state.canStartOperation()) return
    try {
      state.resetPagination()
      state.hasCreationPermission = authManager.hasUserPermission(UserPermission.BOOK_CREATION)
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
        hasCreationPermission = state.hasCreationPermission,
        filterText = state.filterText,
        onFilterTextChange = { newText ->
          state.filterText = newText
          state.isRefreshing = true
        },
        onCreateClick = { state.showCreateDialog = true },
      )

      BooksContent(
        books = state.books,
        isRefreshing = state.isRefreshing,
        isLoadingMore = state.isLoadingMore,
        hasMoreBooks = state.hasMoreBooks,
        hasCreationPermission = state.hasCreationPermission,
        callbacks =
          BooksContentCallbacks(
            onRefresh = { state.isRefreshing = true },
            onBookClick = { book ->
              navigator.navigateTo(Route.BookDetailRoute(book.id, editing = false))
            },
            onBookLongClick = { book -> state.selectedBookForActions = book },
            onLoadMore = { coroutineScope.launch { loadMoreBooks() } },
          ),
      )

      BooksDialogs(
        state = state,
        bookQueryManager = bookQueryManager,
        navigator = navigator,
        coroutineScope = coroutineScope,
      )
    }
  }
}

/**
 * Header section for the Books page.
 *
 * Displays the page title "Books", a filter text field for searching books by name, and, if the
 * user has creation permission, shows an "Add" button to create new books.
 *
 * @param hasCreationPermission Whether the current user has permission to create books.
 * @param filterText The current filter text for searching books.
 * @param onFilterTextChange Callback invoked when the filter text changes.
 * @param onCreateClick Callback invoked when the create button is clicked.
 */
@Composable
private fun BooksHeader(
  hasCreationPermission: Boolean,
  filterText: String,
  onFilterTextChange: (String) -> Unit,
  onCreateClick: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = filterText,
        onValueChange = onFilterTextChange,
        label = { Text("Filter by name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      )
      Text("Books", style = MaterialTheme.typography.headlineMedium)
      if (hasCreationPermission) {
        IconButton(onClick = onCreateClick) {
          Icon(Icons.Default.Add, contentDescription = "Create Book")
        }
      }
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
 * @param hasCreationPermission Whether the user has permission to create/edit books.
 * @param callbacks Collection of callback functions for user interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BooksContent(
  books: List<Book>,
  isRefreshing: Boolean,
  isLoadingMore: Boolean,
  hasMoreBooks: Boolean,
  hasCreationPermission: Boolean,
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
        hasCreationPermission = hasCreationPermission,
        onBookClick = callbacks.onBookClick,
        onBookLongClick = callbacks.onBookLongClick,
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
 * Each book item is clickable and optionally long-clickable (if user has creation permission).
 *
 * @param books The list of books to display.
 * @param isLoadingMore Whether more books are currently being loaded.
 * @param hasMoreBooks Whether there are more books available to load.
 * @param hasCreationPermission Whether the user has permission to edit/delete books.
 * @param onBookClick Callback invoked when a book is clicked.
 * @param onBookLongClick Callback invoked when a book is long-pressed.
 * @param onLoadMore Callback to trigger loading more books.
 */
@Composable
private fun BooksList(
  books: List<Book>,
  isLoadingMore: Boolean,
  hasMoreBooks: Boolean,
  hasCreationPermission: Boolean,
  onBookClick: (Book) -> Unit,
  onBookLongClick: (Book) -> Unit,
  onLoadMore: () -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(books.size, key = { index -> "${books[index].id}-$index" }) { index ->
      val book = books[index]
      BookListItem(
        book = book,
        onClick = { onBookClick(book) },
        onLongClick =
          if (hasCreationPermission) {
            { onBookLongClick(book) }
          } else null,
      )

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
 * Manages all dialog interactions for the Books page.
 *
 * Handles displaying:
 * - Create book dialog when [BooksState.showCreateDialog] is true
 * - Book actions dialog (edit/delete) when a book is selected in
 *   [BooksState.selectedBookForActions]
 *
 * @param state The current state of the Books page.
 * @param bookQueryManager Manager for book-related database operations.
 * @param navigator Navigation controller for page transitions.
 * @param coroutineScope Coroutine scope for launching asynchronous operations.
 */
@Composable
private fun BooksDialogs(
  state: BooksState,
  bookQueryManager: SupabaseBookQueryManager,
  navigator: Navigator,
  coroutineScope: CoroutineScope,
  toastRenderer: ToastRenderer = koinInject(),
) {
  if (state.showCreateDialog) {
    CreateBookDialog(
      onDismiss = { state.showCreateDialog = false },
      onCreate = { name ->
        coroutineScope.launch {
          var bookId: Long? = null
          try {
            bookId = bookQueryManager.createBook(name)
          } catch (e: Exception) {
            LOGGER.e(e) { "Failed to create book." }
            toastRenderer.info("Failed to create book.")
          }
          if (bookId != null) {
            state.showCreateDialog = false
            navigator.navigateTo(Route.BookDetailRoute(bookId, editing = true))
          }
        }
      },
    )
  }

  state.selectedBookForActions?.let { book ->
    BookActionsDialog(
      book = book,
      onDismiss = { state.selectedBookForActions = null },
      onEdit = {
        state.selectedBookForActions = null
        navigator.navigateTo(Route.BookDetailRoute(book.id, editing = true))
      },
      onDelete = {
        coroutineScope.launch {
          try {
            bookQueryManager.deleteBook(book.id)
            state.selectedBookForActions = null
            state.isRefreshing = true
          } catch (e: Exception) {
            toastRenderer.info("Failed to delete book ${book.name}")
            LOGGER.e(e) { "Failed to delete book ${book.id}" }
          }
        }
      },
    )
  }
}

/**
 * Individual book item in the list.
 *
 * Displays a card with the book's name that can be clicked or long-pressed. Regular click navigates
 * to the book detail page. Long press (if enabled) shows edit/delete actions.
 *
 * @param book The book to display.
 * @param onClick Callback invoked when the book card is clicked.
 * @param onLongClick Optional callback invoked when the book card is long-pressed. If null, long
 *   press is disabled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListItem(book: Book, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .padding(vertical = 4.dp)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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

/**
 * Dialog for creating a new book.
 *
 * Displays a text field for entering the book name and buttons to create or cancel. The create
 * button is only enabled when a non-blank name has been entered.
 *
 * @param onDismiss Callback invoked when the dialog is dismissed (cancel button or outside click).
 * @param onCreate Callback invoked when the create button is clicked, receives the entered book
 *   name.
 */
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

/**
 * Dialog displaying available actions for a selected book.
 *
 * Shows options to edit or delete the selected book, with the book's name displayed in the dialog
 * text. Provides three buttons: Edit, Cancel, and Delete.
 *
 * @param book The book for which actions are being displayed.
 * @param onDismiss Callback invoked when the dialog is dismissed (cancel button or outside click).
 * @param onEdit Callback invoked when the edit button is clicked.
 * @param onDelete Callback invoked when the delete button is clicked.
 */
@Composable
private fun BookActionsDialog(
  book: Book,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Book Actions") },
    text = { Text("What would you like to do with \"${book.name}\"?") },
    confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
    dismissButton = {
      Row {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        TextButton(onClick = onDelete) { Text("Delete") }
      }
    },
  )
}

private val LOGGER = Logger.withTag("Books")
