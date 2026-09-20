package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證鳴取牌朝向的共用換算。 */
class MeldActionPopupTileTest {
    /** 上家鳴取往左轉，對家與下家往右轉，暗槓維持直立。 */
    @Test
    fun `claimed tile orientation follows the source direction`() {
        assertEquals(DecisionTileOrientationDto.ROTATED_LEFT, RelativeDirection.Left.claimedTileOrientation())
        assertEquals(DecisionTileOrientationDto.ROTATED_RIGHT, RelativeDirection.Across.claimedTileOrientation())
        assertEquals(DecisionTileOrientationDto.ROTATED_RIGHT, RelativeDirection.Right.claimedTileOrientation())
        assertEquals(DecisionTileOrientationDto.UPRIGHT, RelativeDirection.Self.claimedTileOrientation())
    }
}
