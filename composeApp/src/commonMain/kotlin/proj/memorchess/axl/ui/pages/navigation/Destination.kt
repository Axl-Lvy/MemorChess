package proj.memorchess.axl.ui.pages.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.nav_explore
import memorchess.composeapp.generated.resources.nav_library
import memorchess.composeapp.generated.resources.nav_settings
import memorchess.composeapp.generated.resources.nav_training
import org.jetbrains.compose.resources.StringResource
import proj.memorchess.axl.core.data.PositionKey

/** Possible routes in the application. */
sealed interface Route {

  /** Returns the label for the route, used in navigation bars. */
  fun getLabel(): String

  /** Returns the string resource for the localized display name of the route. */
  fun displayNameRes(): StringResource

  /** Training route. */
  @Serializable
  @SerialName("training")
  data object TrainingRoute : Route {
    override fun getLabel(): String = "Training"

    override fun displayNameRes() = Res.string.nav_training
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

    override fun displayNameRes() = Res.string.nav_explore
  }

  /** Repertoire library route. */
  @Serializable
  @SerialName("library")
  data object LibraryRoute : Route {
    override fun getLabel(): String = "Library"

    override fun displayNameRes() = Res.string.nav_library
  }

  /**
   * Read-only viewer for a single catalog repertoire. Reached from the library; not part of the
   * bottom navigation bar.
   *
   * @property repertoireId Id of the repertoire to display, as listed in the catalog manifest.
   */
  @Serializable
  @SerialName("repertoireView")
  data class RepertoireViewRoute(@SerialName("id") val repertoireId: String) : Route {
    override fun getLabel(): String = "RepertoireView"

    override fun displayNameRes() = Res.string.nav_library
  }

  /** Settings route. */
  @Serializable
  @SerialName("settings")
  data object SettingsRoute : Route {
    override fun getLabel(): String = "Settings"

    override fun displayNameRes() = Res.string.nav_settings
  }
}
