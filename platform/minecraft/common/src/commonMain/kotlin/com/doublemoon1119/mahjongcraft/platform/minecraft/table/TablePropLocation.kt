package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 桌面物件使用的標準位置來源。
 */
sealed interface TablePropLocation {
    /**
     * 依固定座位錨點擺放物件。
     *
     * @property seatIndex 所屬座位索引。
     * @property anchor 座位上的標準錨點。
     * @property offset 相對於錨點的偏移。
     * @property yawOffset 相對座位朝向的額外旋轉角度。
     */
    data class Seat(
        val seatIndex: Int,
        val anchor: TableSeatAnchor,
        val offset: TableSeatOffset = TableSeatOffset(),
        val yawOffset: Float = 0f,
    ) : TablePropLocation {
        init {
            require(seatIndex >= 0) { "Table prop seat index must not be negative: $seatIndex" }
        }
    }

    /**
     * 依平台提供的中央區域順序擺放物件。
     *
     * @property order 同一批中央物件的穩定順序，從零開始。
     * @property yaw 相對桌子標準擺放方向的水平轉角（度）；平台會再加上桌子本身的轉角，不是世界絕對角度。
     */
    data class Center(
        val order: Int,
        val yaw: Float = 0f,
    ) : TablePropLocation {
        init {
            require(order >= 0) { "Table prop center order must not be negative: $order" }
        }
    }
}
