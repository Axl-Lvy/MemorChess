package proj.ankichess.axl

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import proj.ankichess.axl.pages.Home

@Composable
@Preview
fun App() {
  MaterialTheme {
    var navController = rememberNavController()
    NavHost(navController, startDestination = "home") { composable("home") { Home() } }
  }
}

private fun todayDate(): String {
  fun LocalDateTime.format() = toString().substringBefore('T')

  val now = Clock.System.now()
  val zone = TimeZone.currentSystemDefault()
  return now.toLocalDateTime(zone).format()
}
