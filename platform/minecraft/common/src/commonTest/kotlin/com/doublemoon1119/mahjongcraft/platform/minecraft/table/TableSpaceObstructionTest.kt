package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證擺牌層單格是否擋住擺牌的判定。 */
class TableSpaceObstructionTest {
    /** 空氣不擋住。 */
    @Test
    fun `air does not obstruct`() {
        assertFalse(TableSpaceObstruction.isObstructing(isAir = true, hasFluid = false, owningController = null, controller = CONTROLLER))
    }

    /** 本桌自己的方塊可以進入擺牌層。 */
    @Test
    fun `own table block does not obstruct`() {
        assertFalse(TableSpaceObstruction.isObstructing(isAir = false, hasFluid = false, owningController = CONTROLLER, controller = CONTROLLER))
    }

    /** 不屬於任何桌子的方塊會擋住。 */
    @Test
    fun `foreign block obstructs`() {
        assertTrue(TableSpaceObstruction.isObstructing(isAir = false, hasFluid = false, owningController = null, controller = CONTROLLER))
    }

    /** 另一張桌子的方塊也會擋住。 */
    @Test
    fun `block of another table obstructs`() {
        assertTrue(TableSpaceObstruction.isObstructing(isAir = false, hasFluid = false, owningController = OTHER_CONTROLLER, controller = CONTROLLER))
    }

    /** 流體一律擋住，即使所在方塊屬於本桌。 */
    @Test
    fun `fluid obstructs even inside own table block`() {
        assertTrue(TableSpaceObstruction.isObstructing(isAir = false, hasFluid = true, owningController = CONTROLLER, controller = CONTROLLER))
    }

    /** 測試用的控制方塊位置。 */
    private companion object {
        /** 正在檢查的桌子。 */
        val CONTROLLER = Triple(0, 64, 0)

        /** 相鄰的另一張桌子。 */
        val OTHER_CONTROLLER = Triple(3, 64, 0)
    }
}
