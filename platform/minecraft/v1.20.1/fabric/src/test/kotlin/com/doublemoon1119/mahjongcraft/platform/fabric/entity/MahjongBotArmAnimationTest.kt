package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證正常玩家揮手週期、快速揮出後收回，以及動作之外的穩定姿勢。 */
class MahjongBotArmAnimationTest {
    /** 正常玩家的出牌手部回饋不使用機器人獨立的較慢週期。 */
    @Test
    fun `swing lasts six ticks like a player without effects`() {
        assertEquals(6, MahjongBotArmAnimation.DURATION_TICKS)
    }

    /** 最初一個 tick 已接近高點，中段正在收回，而非對稱慢擺。 */
    @Test
    fun `arm swings out early and recovers through the remaining progress`() {
        assertTrue(MahjongBotArmAnimation.swingDegrees(1f / 6f) > 50f)
        assertEquals(10.729968f, MahjongBotArmAnimation.swingDegrees(0.5f), 0.001f)
        assertTrue(MahjongBotArmAnimation.swingDegrees(0.9f) < 1f)
    }

    /** 開始、結束及進度範圍之外不殘留手臂角度。 */
    @Test
    fun `finished or invalid progress returns the resting angle`() {
        listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY).forEach { progress ->
            assertEquals(0f, MahjongBotArmAnimation.swingDegrees(progress))
        }
    }
}
