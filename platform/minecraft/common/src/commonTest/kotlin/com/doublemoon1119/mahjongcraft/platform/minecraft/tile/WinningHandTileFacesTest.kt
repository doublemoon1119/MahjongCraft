package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證和牌展示手牌時副露的蓋牌位置。 */
class WinningHandTileFacesTest {
    /** 依副露資料指定的牌張蓋住兩端。 */
    @Test
    fun `closed kan hides both ends`() {
        val tiles = List(4) { Uuid.random() }
        assertEquals(setOf(0, 3), winningHandFaceDownIndices(tiles, setOf(tiles.first(), tiles.last())))
    }

    /** 其他副露全部翻開。 */
    @Test
    fun `other melds are face up`() {
        val tiles = List(4) { Uuid.random() }
        assertEquals(emptySet(), winningHandFaceDownIndices(tiles, emptySet()))
    }
}
