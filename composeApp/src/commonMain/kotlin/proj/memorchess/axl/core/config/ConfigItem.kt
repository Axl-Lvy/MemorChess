package proj.memorchess.axl.core.config

import org.koin.core.component.KoinComponent

/** A configuration item. */
interface ConfigItem<T : Any> : KoinComponent {

  /** Settings key the value is stored under. */
  val name: String

  /** Default value of the item. */
  val defaultValue: T

  /** Sets the value of the item. */
  fun setValue(value: T)

  /** Gets the value of the item. */
  fun getValue(): T

  /** Resets the value to the default value. */
  fun reset()
}
