package proj.memorchess.axl.ui.pages.navigation

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Tests [Route] serialization through each concrete serializer, as Compose Navigation uses it; the
 * sealed interface has no polymorphic serializer registered.
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

  @Test
  fun todayRouteRoundTripsThroughItsSerializer() {
    val encoded = Json.encodeToString(Route.TodayRoute)
    val decoded = Json.decodeFromString<Route.TodayRoute>(encoded)

    decoded shouldBe Route.TodayRoute
  }
}
