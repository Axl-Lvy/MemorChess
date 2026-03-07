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
    }

    override fun getLabel(): String = "Explore"
  }

  /** Settings route. */
  @Serializable
  @SerialName("settings")
  data object SettingsRoute : Route {
    override fun getLabel(): String = "Settings"
  }

  /** Books route - list of available books. */
  @Serializable
  @SerialName("books")
  data object BooksRoute : Route {
    override fun getLabel(): String = "Books"
  }

  /**
   * Book detail route - view and download book moves.
   *
   * @property bookId The ID of the book to display.
   * @property editing Whether the user can edit the book.
   */
  @Serializable
  @SerialName("book_detail")
  data class BookDetailRoute(
    @SerialName("bookId") val bookId: Long,
    @SerialName("canEdit") val editing: Boolean = false,
  ) : Route {
    override fun getLabel(): String = "Book Detail"
  }
}
