package proj.memorchess.axl.core.config

/** A configuration item. */
interface IConfigItem<T : Any> {

  /** Name of the item. It will be used to store the value in settings. */
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
