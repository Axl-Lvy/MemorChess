package proj.memorchess.axl.ui.pages.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.PositionKey

/** Possible routes in the application. */
sealed interface Route {

  /** Returns the label for the route, used in navigation bars. */
  fun getLabel(): String

  /** Training route. */
  @Serializable
  @SerialName("training")
  data object TrainingRoute : Route {
    override fun getLabel(): String = "Training"
  }

  /**
   * Explore route
   *
   * @property position Optional starting position.
   */
  @Serializable
  @SerialName("explore")
  data class ExploreRoute(
    @SerialName("position") val position: String? = PositionKey.START_POSITION.value
  ) : Route {
    companion object {
      val DEFAULT = ExploreRoute()

      /** Creates an [ExploreRoute] from a typed [PositionKey]. */
      fun from(positionKey: PositionKey) = ExploreRoute(positionKey.value)
    }

    override fun getLabel(): String = "Explore"
  }

  /** Settings route. */
  @Serializable
  @SerialName("settings")
  data object SettingsRoute : Route {
    override fun getLabel(): String = "Settings"
  }
}
