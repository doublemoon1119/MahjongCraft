package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證不同玩家人數下副露來源方向的牌面呈現轉換。 */
class MahjongMeldDisplayDirectionTest {
    /** 兩人對局保留自身方向，其他方向都呈現為對家。 */
    @Test
    fun `two player display collapses non self directions to across`() {
        assertEquals(RelativeDirection.Self, meldDisplayDirection(RelativeDirection.Self, 2))
        assertEquals(RelativeDirection.Across, meldDisplayDirection(RelativeDirection.Left, 2))
        assertEquals(RelativeDirection.Across, meldDisplayDirection(RelativeDirection.Across, 2))
        assertEquals(RelativeDirection.Across, meldDisplayDirection(RelativeDirection.Right, 2))
    }

    /** 三人與四人對局保留規則層記錄的方向。 */
    @Test
    fun `three and four player displays preserve directions`() {
        listOf(3, 4).forEach { playerCount ->
            listOf(
                RelativeDirection.Self,
                RelativeDirection.Left,
                RelativeDirection.Across,
                RelativeDirection.Right,
            ).forEach { direction ->
                assertEquals(direction, meldDisplayDirection(direction, playerCount))
            }
        }
    }

    /** 玩家人數不在支援範圍時拒絕產生呈現方向。 */
    @Test
    fun `invalid player count is rejected`() {
        assertFailsWith<IllegalArgumentException> { meldDisplayDirection(RelativeDirection.Self, 1) }
        assertFailsWith<IllegalArgumentException> { meldDisplayDirection(RelativeDirection.Self, 5) }
    }
}
