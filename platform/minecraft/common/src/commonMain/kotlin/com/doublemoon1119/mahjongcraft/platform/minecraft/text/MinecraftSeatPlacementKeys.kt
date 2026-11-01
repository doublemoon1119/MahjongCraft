package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/** 放置座位物品與安排 AI 入座的提示鍵。 */
object MinecraftSeatPlacementKeys {
    /** 未成功放置，不消耗物品。 */
    const val NOT_PLACED: String = "mahjongcraft.seating.item_not_placed"

    /** 座位已放置但沒有入座，保留物品的正常耗材結果。 */
    const val PLACED_UNSEATED: String = "mahjongcraft.seating.item_placed_unseated"

    /** 提供者執行失敗，需查看診斷。 */
    const val FAILED: String = "mahjongcraft.seating.item_failed"
}
