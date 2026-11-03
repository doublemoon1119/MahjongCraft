package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.math.sqrt

/** 指令與實體空席查詢共用的標準桌子距離，不依桌子外觀判斷。 */
object TableInteractionReach {
    /** 生存模式與非玩家實體的有限可互動距離。 */
    const val SURVIVAL: Double = 4.5

    /** 創造模式玩家的可互動距離。 */
    const val CREATIVE: Double = 5.0

    /**
     * 計算查詢位置到整張標準桌子範圍最近點的距離。
     *
     * @param x 查詢位置的 X。
     * @param y 查詢位置的 Y。
     * @param z 查詢位置的 Z。
     * @param tableX 桌子中心方塊的 X。
     * @param tableY 桌子底層的 Y。
     * @param tableZ 桌子中心方塊的 Z。
     * @return 最近點距離；無效位置回傳無限大。
     */
    fun distance(
        x: Double,
        y: Double,
        z: Double,
        tableX: Int,
        tableY: Int,
        tableZ: Int,
    ): Double {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return Double.POSITIVE_INFINITY
        val dx = x.coerceIn(tableX.toDouble() - MahjongTableSpace.HALF_WIDTH, tableX.toDouble() + MahjongTableSpace.HALF_WIDTH + 1) - x
        val dy = y.coerceIn(tableY.toDouble(), tableY.toDouble() + MahjongTableSpace.PLAY_SPACE_OFFSET_Y + 1) - y
        val dz = z.coerceIn(tableZ.toDouble() - MahjongTableSpace.HALF_WIDTH, tableZ.toDouble() + MahjongTableSpace.HALF_WIDTH + 1) - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
