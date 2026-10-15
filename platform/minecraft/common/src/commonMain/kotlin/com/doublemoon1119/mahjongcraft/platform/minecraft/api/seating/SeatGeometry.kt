package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

/**
 * 乘坐者腳底的位置與身體面向，不是家具或隱形載具的中心。
 *
 * 只描述坐在哪裡、面向哪裡；抬頭或低頭的角度由玩家自行控制，入座時沿用原本的值。由第三方建立時以建構子傳入，
 * 依座標與朝向比較是否相等；`0.0` 與 `-0.0` 表示同一個位置或方向，視為相等。
 *
 * @property x 水平 X 座標。
 * @property y 腳底高度。
 * @property z 水平 Z 座標。
 * @property yaw Minecraft 水平角度；0 度朝南，角度增加時往西轉。
 */
class SeatGeometry(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite() && yaw.isFinite()) { "Seat geometry must be finite" }
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is SeatGeometry &&
        x == other.x &&
        y == other.y &&
        z == other.z &&
        yaw == other.yaw

    /** 加上正零把 `-0.0` 換成 `0.0`，使雜湊與數值相等的 [equals] 一致。 */
    override fun hashCode(): Int {
        var result = (x + 0.0).hashCode()
        result = 31 * result + (y + 0.0).hashCode()
        result = 31 * result + (z + 0.0).hashCode()
        return 31 * result + (yaw + 0.0f).hashCode()
    }

    override fun toString(): String = "SeatGeometry(x=$x, y=$y, z=$z, yaw=$yaw)"
}
