package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [TableLocationRegistry] 的位置、revision 與反向索引測試。 */
class TableLocationRegistryTest {
    /** 呼叫端即使轉成可變 map，也不能改動已發布的桌子位置索引。 */
    @Test
    fun `location snapshots cannot mutate the published index`() {
        val registry = TableLocationRegistry()
        val first = Uuid.random()
        val second = Uuid.random()
        registry.put(first, TableLocation("minecraft:overworld", 1, 64, 1))
        registry.put(second, TableLocation("minecraft:overworld", 2, 64, 2))
        val snapshot = registry.snapshot()
        (snapshot as MutableMap<*, *>).clear()
        assertEquals(setOf(first, second), registry.snapshot().keys)
    }

    /** 不同 dimension 的相同座標必須由不同 chunk key 查詢。 */
    @Test
    fun `test same coordinates in different dimensions remain isolated`() {
        val registry = TableLocationRegistry()
        val overworldTableId = Uuid.random()
        val netherTableId = Uuid.random()
        registry.put(overworldTableId, TableLocation("minecraft:overworld", 32, 64, -17))
        registry.put(netherTableId, TableLocation("minecraft:the_nether", 32, 64, -17))

        assertEquals(
            listOf(overworldTableId),
            registry.getByChunk(DimensionChunkKey("minecraft:overworld", 2, -2)).map { it.tableId },
        )
        assertEquals(
            listOf(netherTableId),
            registry.getByChunk(DimensionChunkKey("minecraft:the_nether", 2, -2)).map { it.tableId },
        )
    }

    /** 同桌移動時應更新反向索引並遞增 revision。 */
    @Test
    fun `test moving table updates chunk index and revision`() {
        val registry = TableLocationRegistry()
        val tableId = Uuid.random()
        val first = registry.put(tableId, TableLocation("minecraft:overworld", 0, 64, 0))
        val unchanged = registry.put(tableId, first.location)
        val moved = registry.put(tableId, TableLocation("minecraft:overworld", 32, 64, 0))

        assertEquals(first, unchanged)
        assertEquals(first.revision + 1, moved.revision)
        assertTrue(registry.getByChunk(DimensionChunkKey("minecraft:overworld", 0, 0)).isEmpty())
        assertEquals(tableId, registry.getByChunk(DimensionChunkKey("minecraft:overworld", 2, 0)).single().tableId)
    }

    /** 過期 revision 不得移除更新後的位置。 */
    @Test
    fun `test stale revision cannot remove current location`() {
        val registry = TableLocationRegistry()
        val tableId = Uuid.random()
        val oldEntry = registry.put(tableId, TableLocation("example:dimension", 0, 0, 0))
        val currentEntry = registry.put(tableId, TableLocation("example:dimension", 16, 0, 0))

        assertFalse(registry.remove(tableId, oldEntry.revision))
        assertEquals(currentEntry, registry.get(tableId))
        assertTrue(registry.remove(tableId, currentEntry.revision))
        assertNull(registry.get(tableId))
    }

    /** 標記無法使用後登記與移除都被拒絕，也不通知保存；清除後恢復可用。 */
    @Test
    fun `unavailable registry rejects changes until cleared`() {
        val registry = TableLocationRegistry()
        val tableId = Uuid.random()
        val location = TableLocation("minecraft:overworld", 0, 64, 0)
        var savedSnapshots = 0
        registry.setDirtyListener { savedSnapshots += 1 }
        registry.markUnavailable()

        assertFailsWith<TableLocationsUnavailableException> { registry.put(tableId, location) }
        assertFailsWith<TableLocationsUnavailableException> { registry.remove(tableId) }

        assertFalse(registry.isAvailable)
        assertNull(registry.get(tableId))
        assertEquals(0, savedSnapshots)
        registry.clear()
        registry.put(tableId, location)
        assertTrue(registry.isAvailable)
        assertEquals(location, registry.get(tableId)?.location)
    }
}
