package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

/**
 * 在同一角色的一次同步互動內，依序驗證、放置及入座；不回復已成功的物品放置。
 *
 * @param K 此次接管的身分型別。
 */
class SeatItemPlacementCoordinator<K : Any> {
    /** 正在放置的角色，避免回呼重入造成重複放置。 */
    private val active = mutableSetOf<K>()

    /**
     * 先檢查請求，放置後再確認接管仍有效，才安排入座。
     *
     * @param key 角色接管身分。
     * @param valid 查詢本次互動是否仍有效且獲准。
     * @param place 放置物品；成功時已完成正常耗材。
     * @param seat 安排原角色入座。
     * @return 放置與入座的結果。
     */
    fun attempt(
        key: K,
        valid: () -> Boolean,
        place: () -> Boolean,
        seat: () -> Boolean,
    ): SeatItemPlacementResult {
        if (!active.add(key)) return SeatItemPlacementResult.BUSY
        try {
            if (!valid()) return SeatItemPlacementResult.REJECTED
            if (!place()) return SeatItemPlacementResult.NOT_PLACED
            return if (valid() && seat()) SeatItemPlacementResult.SEATED else SeatItemPlacementResult.PLACED_UNSEATED
        } finally {
            active.remove(key)
        }
    }
}
