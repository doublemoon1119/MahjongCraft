package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState

/**
 * 不移動乘坐者的家具座位檢查，失敗不能當作確定離座。
 *
 * @property state 本次已確認狀態；FAILED 代表無法可靠判斷。
 * @property geometry 可用或本人已乘坐時的實際幾何，其他狀態為 null。
 */
data class SeatInspection(
    val state: SeatState,
    val geometry: SeatGeometry?,
)
