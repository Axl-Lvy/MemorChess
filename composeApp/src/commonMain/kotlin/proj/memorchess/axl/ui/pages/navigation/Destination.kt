package proj.memorchess.axl.ui.pages.navigation

import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.CompressedPositionIdentifier
import proj.memorchess.axl.core.data.PositionIdentifier

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
    @SerialName("position")
    val stringBytes: String? =
      PositionIdentifier.START_POSITION.toCompressedPosition().bytes.encodeBase64()
  ) : Route {
    constructor(p: PositionIdentifier) : this(p.toCompressedPosition().bytes.encodeBase64())

    constructor(p: CompressedPositionIdentifier) : this(p.bytes.encodeBase64())

    companion object {
      val DEFAULT = ExploreRoute()
    }

    val position
      get() = stringBytes?.let { CompressedPositionIdentifier(it.decodeBase64Bytes()) }

    override fun getLabel(): String = "Explore"
  }

  /** Settings route. */
  @Serializable
  @SerialName("settings")
  data object SettingsRoute : Route {
    override fun getLabel(): String = "Settings"
  }
}
