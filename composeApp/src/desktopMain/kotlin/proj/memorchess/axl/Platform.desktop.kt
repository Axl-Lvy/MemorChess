package proj.memorchess.axl

class AndroidPlatform : Platform {
  override val name: String = "Desktop ${System.getProperty("os.name")}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
