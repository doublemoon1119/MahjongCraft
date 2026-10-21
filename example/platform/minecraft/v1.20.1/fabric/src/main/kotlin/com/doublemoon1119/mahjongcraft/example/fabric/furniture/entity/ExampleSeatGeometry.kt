package com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity

/** 範例家具共用的乘坐高度換算。 */
internal object ExampleSeatGeometry {
    /** 隱形椅子座位 entity 的尺寸。 */
    const val CHAIR_SEAT_SIZE: Float = 0.25f

    /** 椅面相對方塊底部的高度。 */
    const val CHAIR_TOP_HEIGHT: Double = 0.5625

    /** 坐姿腳底低於椅面的距離，使坐姿模型落在椅面上。 */
    const val SEATED_BOTTOM_HEIGHT: Double = 0.586

    /** 依椅面高度算隱形座位 entity 的位置。 */
    fun chairSeatY(blockY: Int): Double = blockY + CHAIR_TOP_HEIGHT - CHAIR_SEAT_SIZE

    /** 依隱形座位 entity 的位置算乘客腳底高度。 */
    fun passengerFeetY(seatY: Double): Double = seatY + CHAIR_SEAT_SIZE - SEATED_BOTTOM_HEIGHT

    /** 坐墊 entity 的乘客腳底高度。 */
    fun cushionFeetY(entityY: Double): Double = entityY + 0.2 - SEATED_BOTTOM_HEIGHT
}
