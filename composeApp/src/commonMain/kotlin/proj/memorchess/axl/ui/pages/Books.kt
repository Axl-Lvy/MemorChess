package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.data.book.UserPermission
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route

/**
 * Books page that displays available books.
 *
 * Users can browse books and download their moves. Users with BOOK_CREATION permission can also
 * create new books.
 */
@Composable
fun Books(bookQueryManager: BookQueryManager = koinInject(), navigator: Navigator = koinInject()) {
  var books by remember { mutableStateOf<List<Book>>(emptyList()) }
  var hasCreationPermission by remember { mutableStateOf(false) }
  var showCreateDialog by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp).testTag("books"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LoadingWidget({
      books = bookQueryManager.getAllBooks()
      hasCreationPermission = bookQueryManager.hasPermission(UserPermission.BOOK_CREATION)
    }) {
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(books, key = { it.id }) { book ->
            BookListItem(book = book) { navigator.navigateTo(Route.BookDetailRoute(book.id)) }
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
              navigator.navigateTo(Route.BookCreationRoute(bookId))
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
