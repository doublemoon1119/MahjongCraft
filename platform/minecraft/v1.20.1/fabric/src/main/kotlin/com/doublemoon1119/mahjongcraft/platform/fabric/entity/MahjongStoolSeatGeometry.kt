package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.util.math.Direction

/**
 * 麻將凳座位的位置換算與下座順序。
 *
 * 座位 entity 嵌在凳面下方，頂端與凳面齊平，因此座位所在的方塊就是凳子本身。
 * 乘客的腳底放在凳面下方 [SEATED_BOTTOM_HEIGHT] 處，使人形乘客坐姿的屁股底面貼齊凳面。
 */
internal object MahjongStoolSeatGeometry {
    /** 座位 entity 的寬度與高度，以方塊為單位。 */
    const val SEAT_SIZE: Float = 0.25f

    /**
     * 人形乘客坐姿時，屁股底面相對腳底的高度，以方塊為單位。
     *
     * 原版玩家模型繪製時縮放為 `15/16`；坐姿的大腿往前平伸，底面在臀部關節（腳底上方 12 像素）下方 2 像素，
     * 因此屁股底面位於腳底上方 `10/16 × 15/16` 格。
     */
    const val SEATED_BOTTOM_HEIGHT: Double = 10.0 / 16.0 * 15.0 / 16.0

    /** 凳面高度為 [seatTopY] 時，座位 entity 的 Y 座標。 */
    fun seatY(seatTopY: Double): Double = seatTopY - SEAT_SIZE

    /** 座位 entity 位於 [seatY] 時，乘客腳底的 Y 座標。 */
    fun passengerFeetY(seatY: Double): Double = seatY + SEAT_SIZE - SEATED_BOTTOM_HEIGHT

    /** 乘客面對 [facing] 時嘗試下座的方向：先往面前，再往右、往左，最後往後。 */
    fun dismountDirections(facing: Direction): List<Direction> = listOf(
        facing,
        facing.rotateYClockwise(),
        facing.rotateYCounterclockwise(),
        facing.opposite,
    )
}
