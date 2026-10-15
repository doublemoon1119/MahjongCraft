package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

import kotlin.uuid.Uuid

/**
 * 固定席位請求，只由 MahjongCraft 建立；不授權改動牌局或尋找替代座位。
 *
 * 實體來源只查詢標準座位水平各一格、垂直上下各兩格的已載入區域，再驗證實際乘坐點。
 *
 * 提供者以 [SeatTarget.geometry] 回報的實際乘坐點，必須同時符合以下條件才會被採用，邊界值都包含在內：
 * - 座位所在維度與 [dimensionId] 相同。
 * - 水平距離：腳底與 [seat] 在 XZ 平面上的直線距離不超過 0.25 格。
 * - 高度：腳底與 [seat] 的高度差不超過 1.0 格，容許不同高度的椅面，但不接受樓上的座位。
 * - 朝向：與 [seat] 的水平角度差換算到 -180 度至 180 度後，絕對值不超過 15 度。
 *
 * 不符合的候選會被忽略，不會用來入座；沒有可用的候選時，乘坐者改站在標準座位上。這些數值是目前的實作，
 * 將來可能調整。
 *
 * @property tableId 桌子識別碼，供診斷使用。
 * @property seatIndex 固定席位 index。
 * @property dimensionId 目標維度。
 * @property seat 標準座位：位於桌子一側的中央、離桌子中心 2 格，腳底與桌子最下層方塊的底面同高，面向桌子中心。
 */
class SeatRequest private constructor(
    val tableId: Uuid,
    val seatIndex: Int,
    val dimensionId: String,
    val seat: SeatGeometry,
) {
    override fun toString(): String = "SeatRequest(tableId=$tableId, seatIndex=$seatIndex, dimensionId=$dimensionId, seat=$seat)"

    /** 座位安排內部建立請求的入口。 */
    internal companion object {
        /**
         * 建立一個固定席位請求。
         *
         * @param tableId 桌子識別碼。
         * @param seatIndex 固定席位 index。
         * @param dimensionId 目標維度。
         * @param seat 標準座位。
         * @return 建立的請求。
         */
        @JvmSynthetic
        fun create(
            tableId: Uuid,
            seatIndex: Int,
            dimensionId: String,
            seat: SeatGeometry,
        ): SeatRequest = SeatRequest(tableId, seatIndex, dimensionId, seat)
    }
}
