package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證和牌展示手牌時副露的蓋牌位置。 */
class WinningHandTileFacesTest {
    /** 暗槓蓋住兩端、翻開中間兩張。 */
    @Test
    fun `closed kan hides both ends`() {
        assertEquals(setOf(0, 3), winningHandFaceDownIndices(MeldType.CLOSED_KAN, 4))
    }

    /** 其他副露全部翻開。 */
    @Test
    fun `other melds are face up`() {
        listOf(MeldType.CHI, MeldType.PON, MeldType.OPEN_KAN, MeldType.ADDED_KAN).forEach { type ->
            assertEquals(emptySet(), winningHandFaceDownIndices(type, 4), type.toString())
        }
    }
}
