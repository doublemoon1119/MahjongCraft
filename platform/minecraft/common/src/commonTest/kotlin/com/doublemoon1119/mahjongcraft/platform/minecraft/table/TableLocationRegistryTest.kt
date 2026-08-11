package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [TableLocationRegistry] 的位置、revision 與反向索引測試。 */
class TableLocationRegistryTest {
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
}
