package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

/** 自家暫時乘坐支撐的占用判斷與清理，唯讀查詢不刪除支撐。 */
internal object SeatSupportCleanup {
    /**
     * 只把有乘客的支撐視為占用，空支撐可留待實際入座時清理。
     *
     * @param S 自家支撐的型別。
     * @param supports 目前位置尚未移除的自家支撐。
     * @param hasPassengers 讀取支撐是否有乘客，不修改狀態。
     * @return 至少一個支撐有乘客時為 true。
     */
    fun <S> isOccupied(supports: List<S>, hasPassengers: (S) -> Boolean): Boolean = supports.any(hasPassengers)

    /**
     * 入座前清理自家空支撐；已有乘客時不清理，且每次刪除前都重新確認。
     *
     * 此操作需在世界執行緒同步完成。傳入集合只限呼叫者自己建立的支撐，不包含椅子方塊、坐墊或其他第三方家具。
     *
     * @param S 自家支撐的型別。
     * @param supports 本次入座位置的自家支撐。
     * @param hasPassengers 讀取支撐是否有乘客，不修改狀態。
     * @param discard 移除已確認沒有乘客的自家支撐。
     * @return 本批支撐皆可安全清理時為 true；遇到乘客時停止，回傳 false。
     */
    fun <S> clearEmpty(
        supports: List<S>,
        hasPassengers: (S) -> Boolean,
        discard: (S) -> Unit,
    ): Boolean {
        if (isOccupied(supports, hasPassengers)) return false
        for (support in supports) {
            if (hasPassengers(support)) return false
            discard(support)
        }
        return true
    }
}
