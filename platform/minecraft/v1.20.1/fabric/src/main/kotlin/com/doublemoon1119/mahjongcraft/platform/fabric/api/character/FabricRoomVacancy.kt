package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import kotlin.uuid.Uuid

/**
 * 只供伺服器整合使用的當下空席提示，不預約席位，也不授予之後的加入權限。
 *
 * @property tableId 可加入的桌子，不可轉成未授權的客戶端座標查詢。
 * @property roomInstanceId 查詢當下的房間生命週期，解散後失效。
 * @property seatIndex 本桌四側等待席位的編號，不是開局後的正式名冊編號。
 * @property geometry 固定席位的腳底位置與面向。
 * @property hasFurniture 當下已有可用座位家具，而不只是可安全站立。
 */
class FabricRoomVacancy private constructor(
    val tableId: Uuid,
    val roomInstanceId: Uuid,
    val seatIndex: Int,
    val geometry: SeatGeometry,
    val hasFurniture: Boolean,
) {
    /** 由伺服器的有界授權查詢建立結果。 */
    companion object {
        /**
         * 擷取已通過檢查的席位。
         *
         * @param tableId 桌子識別碼。
         * @param roomInstanceId 房間生命週期。
         * @param seatIndex 本桌等待席位編號。
         * @param geometry 已驗證的固定位置。
         * @param hasFurniture 是否已有可入座家具。
         * @return 唯讀提示。
         */
        @JvmSynthetic
        internal fun create(
            tableId: Uuid,
            roomInstanceId: Uuid,
            seatIndex: Int,
            geometry: SeatGeometry,
            hasFurniture: Boolean,
        ): FabricRoomVacancy = FabricRoomVacancy(tableId, roomInstanceId, seatIndex, geometry, hasFurniture)
    }
}
