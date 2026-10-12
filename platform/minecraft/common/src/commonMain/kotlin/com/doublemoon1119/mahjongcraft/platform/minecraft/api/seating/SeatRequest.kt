package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatPlacement
import kotlin.uuid.Uuid

/**
 * 固定席位請求，不授權改動牌局或尋找替代座位。
 *
 * 實體來源只查詢錨點水平各一格、垂直上下各兩格的已載入區域，再驗證實際乘坐點。
 *
 * @property tableId 桌子識別碼，供診斷使用。
 * @property seatIndex 固定席位 index。
 * @property dimensionId 目標維度。
 * @property placement 固定位置與朝向。
 */
data class SeatRequest(
    val tableId: Uuid,
    val seatIndex: Int,
    val dimensionId: String,
    val placement: MahjongSeatPlacement,
)
