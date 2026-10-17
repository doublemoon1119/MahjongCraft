package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證本地牌張位移後的射線命中、遮擋與互動距離。 */
class HandTileTargetingTest {
    /** 只檢查顯示後的包圍盒，舊位置不再可選。 */
    @Test
    fun `translated box is selected while old position is not`() {
        val start = Vec3d(0.0, 0.5, 0.0)
        val end = Vec3d(5.0, 0.5, 0.0)

        assertEquals("new", findNearestHandTileTarget(start, end, listOf("new" to Box(2.0, 0.0, -0.5, 3.0, 1.0, 0.5)))?.payload)
        assertNull(findNearestHandTileTarget(start, end, listOf("old" to Box(0.0, 2.0, -0.5, 1.0, 3.0, 0.5))))
    }

    /** 最近物件優先，且不能選取遮擋物後方的牌。 */
    @Test
    fun `nearer candidate wins and block distance occludes`() {
        val start = Vec3d(0.0, 0.5, 0.0)
        val end = Vec3d(5.0, 0.5, 0.0)
        val candidates = listOf(
            "near" to Box(1.0, 0.0, -0.5, 2.0, 1.0, 0.5),
            "far" to Box(3.0, 0.0, -0.5, 4.0, 1.0, 0.5),
        )

        assertEquals("near", findNearestHandTileTarget(start, end, candidates)?.payload)
        assertNull(findNearestHandTileTarget(start, end, candidates, occlusionDistanceSquared = 0.5 * 0.5))
    }

    /** 候選即使在射線上，也不能越過原本有效的命中距離。 */
    @Test
    fun `candidate beyond maximum distance is not selected`() {
        val start = Vec3d(0.0, 0.5, 0.0)
        val end = Vec3d(5.0, 0.5, 0.0)

        assertNull(
            findNearestHandTileTarget(
                start,
                end,
                listOf("far" to Box(3.0, 0.0, -0.5, 4.0, 1.0, 0.5)),
                maximumDistanceSquared = 2.0 * 2.0,
            ),
        )
    }

    /** 沒有候選時安全回傳未命中。 */
    @Test
    fun `empty candidates are safe`() {
        assertNull(findNearestHandTileTarget<String>(Vec3d.ZERO, Vec3d(1.0, 0.0, 0.0), emptyList()))
    }
}
