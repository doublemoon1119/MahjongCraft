package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證方塊區塊先載入、實體後載入時不會太早重建桌面物件。 */
class FabricTablePropSearchAreaTest {
    /** 方塊已可用但保存實體尚未就緒時，場景必須繼續等待。 */
    @Test
    fun `loaded blocks are not enough before saved entities arrive`() {
        assertFalse(FabricTablePropSearchArea.entitiesLoaded(BlockPos(0, 64, 0), { _, _ -> true }, { false }))
        assertFalse(FabricTablePropSearchArea.entitiesLoaded(BlockPos(0, 64, 0), { _, _ -> false }, { true }))
    }

    /** 跨區塊桌子必須檢查整個實體搜尋範圍，包括負座標。 */
    @Test
    fun `every entity chunk in the search area must be available`() {
        val queried = mutableSetOf<Long>()
        assertTrue(
            FabricTablePropSearchArea.entitiesLoaded(
                pos = BlockPos(0, 64, 0),
                blocksLoaded = { _, _ -> true },
                savedEntitiesLoaded = {
                    queried += it
                    true
                },
            ),
        )
        assertEquals(setOf(ChunkPos.toLong(-1, -1), ChunkPos.toLong(-1, 0), ChunkPos.toLong(0, -1), ChunkPos.toLong(0, 0)), queried)
        assertFalse(
            FabricTablePropSearchArea.entitiesLoaded(
                pos = BlockPos(0, 64, 0),
                blocksLoaded = { _, _ -> true },
                savedEntitiesLoaded = { it != ChunkPos.toLong(-1, -1) },
            ),
        )
    }

    /** 無法找回的遠處落點和非有限座標不能生成，以免每次同步都重複建立。 */
    @Test
    fun `prop targets must stay inside the area where managed entities are found`() {
        val pos = BlockPos(0, 64, 0)
        assertTrue(FabricTablePropSearchArea.contains(pos, 0.5, 65.0, 0.5))
        assertFalse(FabricTablePropSearchArea.contains(pos, 100.0, 65.0, 0.5))
        assertFalse(FabricTablePropSearchArea.contains(pos, 0.5, 65.0, Double.NaN))
        assertFalse(FabricTablePropSearchArea.contains(pos, 0.5, Double.POSITIVE_INFINITY, 0.5))
    }
}
