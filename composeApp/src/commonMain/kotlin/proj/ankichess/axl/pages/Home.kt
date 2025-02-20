package proj.ankichess.axl.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Home() {
  Scaffold(topBar = { TopAppBar(title = { Text("Home Menu") }) }) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "Welcome!",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 32.dp),
      )

      Button(onClick = { /* TODO: Navigate to Screen 1 */ }, modifier = Modifier.fillMaxWidth()) {
        Text("Go to Screen 1")
      }
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = { /* TODO: Navigate to Screen 2 */ }, modifier = Modifier.fillMaxWidth()) {
        Text("Go to Screen 2")
      }
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = { /* TODO: Navigate to Screen 3 */ }, modifier = Modifier.fillMaxWidth()) {
        Text("Go to Screen 3")
      }
    }
  }
}
