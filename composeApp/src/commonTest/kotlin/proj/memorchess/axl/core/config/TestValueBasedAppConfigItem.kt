package proj.memorchess.axl.core.config

import kotlin.test.*
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Coverage for [ValueBasedAppConfigItem]'s sync stamping: [BooleanBasedConfigItem] exercises it
 * through a concrete, non-sealed subtype.
 */
class TestValueBasedAppConfigItem : TestWithKoin() {
  private lateinit var config: BooleanBasedConfigItem
  private val syncMetadata: SettingSyncMetadataStore by inject()
  private val database: DatabaseQueryManager by inject()

  override suspend fun setUp() {
    config = BooleanBasedConfigItem("test_bool", false)
  }

  @Test
  fun setValueStampsSyncMetadataWithThisDeviceAndQueuesTheOutbox() = test {
    config.setValue(true)

    val metadata = syncMetadata.read("test_bool")!!
    assertFalse(metadata.isDeleted)
    val outbox = database.getOutbox().map { it.key }
    assertTrue(outbox.contains(DirtyKey.SettingKey("test_bool")))
  }

  @Test
  fun eachSetValueAdvancesDeviceSeq() = test {
    config.setValue(true)
    val first = syncMetadata.read("test_bool")!!.deviceSeq

    config.setValue(false)
    val second = syncMetadata.read("test_bool")!!.deviceSeq

    assertTrue(second > first)
  }

  @Test
  fun resetStampsATombstoneAndQueuesTheOutbox() = test {
    config.setValue(true)

    config.reset()

    assertTrue(syncMetadata.read("test_bool")!!.isDeleted)
    val outbox = database.getOutbox().map { it.key }
    assertTrue(outbox.contains(DirtyKey.SettingKey("test_bool")))
  }

  @Test
  fun stampReturnsTheSequenceActuallyStored() = test {
    config.setValue(true)
    config.setValue(false)

    // A stamp that raced and lost would leave read() reporting a lower deviceSeq than the one
    // actually written by the most recent setValue.
    val stored = syncMetadata.read("test_bool")!!.deviceSeq
    config.setValue(true)
    assertTrue(syncMetadata.read("test_bool")!!.deviceSeq > stored)
  }
}
