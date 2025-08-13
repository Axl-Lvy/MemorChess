package proj.memorchess.axl.core.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import org.koin.core.component.inject
import proj.memorchess.axl.core.util.CanDisplayName

/**
 * Config item based on an enum. Use [from] to create one.
 *
 * @param T The enum type
 * @property getEntries A function that returns the entries of the enum
 */
class EnumBasedAppConfigItem<T>(
  override val name: String,
  override val defaultValue: T,
  valueOf: (String) -> T,
  val getEntries: () -> Array<T>,
) : ConfigItem<T> where T : Enum<T>, T : CanDisplayName {

  private val settings: Settings by inject()

  private var localValue by
    mutableStateOf(
      try {
        valueOf(settings[name, defaultValue.name])
      } catch (_: IllegalArgumentException) {
        defaultValue
      }
    )

  companion object {
    inline fun <reified T> from(name: String, default: T): EnumBasedAppConfigItem<T> where
    T : Enum<T>,
    T : CanDisplayName {
      return EnumBasedAppConfigItem(name, default, { enumValueOf<T>(it) }, { enumValues<T>() })
    }
  }

  override fun getValue(): T {
    return localValue
  }

  override fun setValue(value: T) {
    this.localValue = value
    settings[name] = value.name
  }

  override fun reset() {
    settings.remove(name)
    localValue = defaultValue
  }
}
