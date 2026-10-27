@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package proj.memorchess.axl

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration
import proj.memorchess.axl.core.auth.OidcClient
import proj.memorchess.axl.core.auth.OidcRedirectDecision
import proj.memorchess.axl.core.auth.OidcTokenStore
import proj.memorchess.axl.core.auth.PendingOidcRedirectStore
import proj.memorchess.axl.core.auth.SYNC_CLIENT_ID
import proj.memorchess.axl.core.auth.SYNC_REDIRECT_PATH
import proj.memorchess.axl.core.auth.SYNC_REDIRECT_URI
import proj.memorchess.axl.core.auth.decideOidcRedirectExchange
import proj.memorchess.axl.core.auth.exchangeOidcRedirectCode
import proj.memorchess.axl.core.auth.parseDecodedQuery
import proj.memorchess.axl.core.config.getPlatformSpecificSettings
import proj.memorchess.axl.core.sync.SyncEngine
import proj.memorchess.axl.ui.App
import proj.memorchess.axl.ui.pages.navigation.routeFromHash

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
  // Must run before ComposeViewport: NavHost commits its start route before
  // bindToBrowserNavigation attaches, so the callback URL is cleaned up first and startRoute is
  // computed from the hash rather than left to the binder.
  val pendingStore = PendingOidcRedirectStore(getPlatformSpecificSettings())
  val decision =
    decideOidcRedirectExchange(
      currentPath = window.location.pathname,
      redirectPath = SYNC_REDIRECT_PATH,
      queryParams = parseDecodedQuery(window.location.search),
      pending = pendingStore.load(),
    )
  val pendingExchange =
    when (decision) {
      is OidcRedirectDecision.Callback -> {
        window.history.replaceState(null, "", decision.cleanedUrl)
        pendingStore.clear()
        decision.exchange
      }
      OidcRedirectDecision.NotACallback -> null
    }
  // Read after the callback cleanup, so a refresh mid redirect resolves the pre-sign-in hash.
  val startRoute = routeFromHash(window.location.hash)

  ComposeViewport(document.body ?: return) {
    KoinApplication(configuration = koinConfiguration { modules(*initKoinModules()) }) {
      if (pendingExchange != null) {
        val oidcClient = koinInject<OidcClient>()
        val tokenStore = koinInject<OidcTokenStore>()
        val syncEngine = koinInject<SyncEngine>()
        LaunchedEffect(Unit) {
          exchangeOidcRedirectCode(
            oidcClient = oidcClient,
            tokenStore = tokenStore,
            clientId = SYNC_CLIENT_ID,
            redirectUri = SYNC_REDIRECT_URI,
            exchange = pendingExchange,
            onSignedIn = { syncEngine.syncNow() },
          )
        }
      }
      App(startRoute = startRoute) {
        it.callDelegate { navHostController -> navHostController.bindToBrowserNavigation() }
      }
    }
  }
}
