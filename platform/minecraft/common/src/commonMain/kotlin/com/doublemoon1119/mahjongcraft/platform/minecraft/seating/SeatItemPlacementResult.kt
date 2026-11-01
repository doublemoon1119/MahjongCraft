package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

/** 一次座位物品互動的結果，區分放置失敗與已放置但未入座。 */
enum class SeatItemPlacementResult {
    /** 請求已失效或不允許放置。 */
    REJECTED,

    /** 同一角色已有進行中的放置。 */
    BUSY,

    /** 沒有成功放置物品。 */
    NOT_PLACED,

    /** 放置成功且角色已入座。 */
    SEATED,

    /** 放置已成功，但角色未能入座；保留放置結果。 */
    PLACED_UNSEATED,
}
