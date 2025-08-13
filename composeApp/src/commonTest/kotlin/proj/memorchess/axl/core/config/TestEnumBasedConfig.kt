package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.test.*
import org.koin.core.component.inject
import proj.memorchess.axl.core.util.CanDisplayName
import proj.memorchess.axl.test_util.TestWithKoin

private enum class TestEnum(override val displayName: String) : CanDisplayName {
  FIRST("First"),
  SECOND("Second"),
  THIRD("Third"),
}

class TestEnumBasedConfig : TestWithKoin {
  private lateinit var config: EnumBasedAppConfigItem<TestEnum>
  private val settings: Settings by inject()

  @BeforeTest
  override fun setUp() {
    super.setUp()
    config = EnumBasedAppConfigItem.from("test_enum", TestEnum.SECOND)
  }

  @Test
  fun defaultValueIsReturnedInitially() {
    assertEquals(TestEnum.SECOND, config.getValue())
  }

  @Test
  fun setValueUpdatesValueAndPersists() {
    config.setValue(TestEnum.THIRD)
    assertEquals(TestEnum.THIRD, config.getValue())
  }

  @Test
  fun resetRestoresDefaultAndRemovesPersisted() {
    config.setValue(TestEnum.FIRST)
    config.reset()
    assertEquals(TestEnum.SECOND, config.getValue())
  }

  @Test
  fun invalidPersistedValueFallsBackToDefault() {
    settings.putString("test_enum", "INVALID")
    val newConfig = EnumBasedAppConfigItem.from("test_enum", TestEnum.FIRST)
    assertEquals(TestEnum.FIRST, newConfig.getValue())
  }

  @Test
  fun getEntriesReturnsAllEnumValues() {
    val entries = config.getEntries().toList()
    assertEquals(listOf(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD), entries)
  }
}
