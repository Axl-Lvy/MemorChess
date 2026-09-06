package proj.memorchess.axl.ui.pages.navigation

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serialization coverage for [Route]'s scoped variants, which Compose Navigation relies on to
 * carry a route's arguments across a process death. Round trips go through each concrete type's
 * own generated serializer, exactly the way `composable<Route.TrainingRoute>`/`toRoute` in
 * `Router.kt` use it, not the polymorphic `Route` serializer (which this sealed interface, unlike
 * a sealed class, needs an explicit `SerializersModule` to support and neither Compose Navigation
 * nor this app ever registers one).
 */
class TestDestination {

  @Test
  fun trainingRouteWithARepertoireIdRoundTripsThroughItsSerializer() {
    val route = Route.TrainingRoute(repertoireId = "italian-game")

    val encoded = Json.encodeToString(route)
    val decoded = Json.decodeFromString<Route.TrainingRoute>(encoded)

    decoded shouldBe route
  }

  @Test
  fun exploreRouteWithARepertoireIdRoundTripsThroughItsSerializer() {
    val route = Route.ExploreRoute(position = "posA b K", repertoireId = "italian-game")

    val encoded = Json.encodeToString(route)
    val decoded = Json.decodeFromString<Route.ExploreRoute>(encoded)

    decoded shouldBe route
  }

  @Test
  fun trainingRouteDefaultHasANullRepertoireId() {
    Route.TrainingRoute.DEFAULT shouldBe Route.TrainingRoute(repertoireId = null)
  }
}
