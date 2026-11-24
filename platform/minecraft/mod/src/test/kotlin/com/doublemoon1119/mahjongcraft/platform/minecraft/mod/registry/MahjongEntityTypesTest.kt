package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 [MahjongEntityTypes] 的追蹤距離換算。 */
class MahjongEntityTypesTest {
    /** 方塊距離換算成區塊時，不足一個區塊的部分進位。 */
    @Test
    fun `block distances round up to whole chunks`() {
        assertEquals(0, MahjongEntityTypes.chunksCovering(blocks = 0))
        assertEquals(1, MahjongEntityTypes.chunksCovering(blocks = 1))
        assertEquals(1, MahjongEntityTypes.chunksCovering(blocks = 16))
        assertEquals(2, MahjongEntityTypes.chunksCovering(blocks = 17))
        assertEquals(2, MahjongEntityTypes.chunksCovering(blocks = 32))
    }
}
