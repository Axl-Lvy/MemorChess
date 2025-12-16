package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.UserPermission
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route

/**
 * Books page that displays available books.
 *
 * Users can browse books and download their moves. Users with BOOK_CREATION permission can also
 * create new books.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Books(
  bookQueryManager: SupabaseBookQueryManager = koinInject(),
  navigator: Navigator = koinInject(),
  authManager: AuthManager = koinInject(),
) {
  var books by remember { mutableStateOf<List<Book>>(emptyList()) }
  var hasCreationPermission by remember { mutableStateOf(false) }
  var isRefreshing by remember { mutableStateOf(false) }
  var showCreateDialog by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  suspend fun refreshBooksList() {
    books = bookQueryManager.getAllBooks()
    hasCreationPermission = authManager.hasUserPermission(UserPermission.BOOK_CREATION)
  }

  LoadingWidget({ refreshBooksList() }) {
    Column(
      modifier =
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp).testTag("books"),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
          refreshBooksList()
          isRefreshing = false
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Books", style = MaterialTheme.typography.headlineMedium)
        if (hasCreationPermission) {
          IconButton(onClick = { showCreateDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = "Create Book")
          }
        }
      }

      if (books.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No books available", style = MaterialTheme.typography.bodyLarge)
        }
      } else {
        PullToRefreshBox(isRefreshing, { isRefreshing = true }) {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(books, key = { it.id }) { book ->
              BookListItem(book = book) { navigator.navigateTo(Route.BookDetailRoute(book.id)) }
            }
          }
        }
      }

      if (showCreateDialog) {
        CreateBookDialog(
          onDismiss = { showCreateDialog = false },
          onCreate = { name ->
            coroutineScope.launch {
              val bookId = bookQueryManager.createBook(name)
              showCreateDialog = false
              navigator.navigateTo(Route.BookDetailRoute(bookId))
            }
          },
        )
      }
    }
  }
}

@Composable
private fun BookListItem(book: Book, onClick: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(book.name, style = MaterialTheme.typography.titleMedium)
    }
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
