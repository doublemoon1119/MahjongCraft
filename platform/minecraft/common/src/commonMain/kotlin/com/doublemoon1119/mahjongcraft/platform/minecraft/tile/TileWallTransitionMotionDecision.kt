package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/** 牌牆 transition 路徑規劃結果。 */
sealed interface TileWallTransitionMotionDecision {
    /**
     * 所有移動均已成功規劃。
     *
     * @property plan 完整的牌牆移動計畫。
     */
    data class Completed(val plan: TileWallTransitionMotionPlan) : TileWallTransitionMotionDecision

    /**
     * 至少一個移動無法轉成安全路徑。
     *
     * @property tileId 無法規劃安全路徑的牌張 UUID。
     * @property reason 路徑規劃失敗的原因。
     */
    data class Rejected(
        val tileId: Uuid,
        val reason: TileWallMotionPathRejection,
    ) : TileWallTransitionMotionDecision
}
