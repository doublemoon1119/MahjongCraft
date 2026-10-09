package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

/**
 * 事件產生時麻將桌所在的世界位置。
 *
 * @property dimensionId 所在維度的 namespaced ID。
 * @property x 方塊 X 座標。
 * @property y 方塊 Y 座標。
 * @property z 方塊 Z 座標。
 */
class TableLocation private constructor(
    val dimensionId: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    /** MahjongCraft 內部建立公開事件位置的入口。 */
    internal companion object {
        /**
         * 建立事件位置快照。
         *
         * @param dimensionId 所在維度的 namespaced ID。
         * @param x 方塊 X 座標。
         * @param y 方塊 Y 座標。
         * @param z 方塊 Z 座標。
         */
        @JvmSynthetic
        fun create(
            dimensionId: String,
            x: Int,
            y: Int,
            z: Int,
        ): TableLocation = TableLocation(dimensionId, x, y, z)
    }
}
