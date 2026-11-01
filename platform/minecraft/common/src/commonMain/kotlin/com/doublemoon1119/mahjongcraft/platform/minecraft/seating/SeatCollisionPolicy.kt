package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

/** 座位空間只受實際身體碰撞影響，純顯示物件不占位；已有座位另由 provider 查詢。 */
object SeatCollisionPolicy {
    /**
     * 判斷查詢範圍內的物件是否阻擋座位空間。
     *
     * @param removed 是否已移除。
     * @param spectator 是否為旁觀模式的物件。
     * @param physical 是否具有實際身體碰撞，包含玩家與其他乘坐者。
     * @return 仍存在、非旁觀且具有身體碰撞時為 true。
     */
    fun blocks(
        removed: Boolean,
        spectator: Boolean,
        physical: Boolean,
    ): Boolean = !removed && !spectator && physical
}
