package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState

/** 範例家具共用的占用、互動與身分判定。 */
internal object ExampleFurnitureSeatRules {
    /**
     * 判斷玩家是否可拆除目前的空坐墊，不讓重複攻擊或占用中的拆除產生掉落。
     *
     * @param removed 坐墊是否已移除。
     * @param occupied 坐墊是否有乘客。
     * @param canModify 玩家是否有權修改坐墊位置。
     * @return 坐墊仍存在、無乘客且有權限時為 true。
     */
    fun canBreakCushion(removed: Boolean, occupied: Boolean, canModify: Boolean): Boolean = !removed && !occupied && canModify

    /** 依乘坐者與目標目前狀態決定可用、占用或不支援。 */
    fun occupancyState(hasVehicle: Boolean, occupied: Boolean): SeatState = when {
        hasVehicle -> SeatState.UNSUPPORTED
        occupied -> SeatState.OCCUPIED
        else -> SeatState.AVAILABLE
    }

    /**
     * 以實體 UUID 建立坐墊身分，不使用重進世界後會改變的數字 entity ID。
     *
     * @param dimensionId 所在維度。
     * @param uuidAsString 原版實體的 UUID 字串。
     * @return 具有固定 slot 的實體座位身分。
     */
    fun cushionIdentity(dimensionId: String, uuidAsString: String): SeatIdentity = SeatIdentity(
        dimensionId = dimensionId,
        kind = "minecraft:entity",
        key = uuidAsString,
    )
}
