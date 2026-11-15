package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

/** 牌譜列表要求的一頁在全部對局中的位置，用來在回覆到達後算出第一筆是第幾筆。 */
internal sealed interface ListPosition {
    /**
     * 算出回覆頁第一筆的 1-based 位置。
     *
     * @param count 回覆頁的筆數。
     * @param totalCount 回覆的對局總數。
     * @param atStart 回覆頁之前是否已經沒有對局。
     * @return 第一筆的位置；空頁為 0。
     */
    fun firstEntryIndex(count: Int, totalCount: Int, atStart: Boolean): Int

    /**
     * 從指定位置往後的一頁，例如第一頁或下一頁。
     *
     * @property index 第一筆的 1-based 位置。
     */
    data class StartsAt(val index: Int) : ListPosition {
        override fun firstEntryIndex(count: Int, totalCount: Int, atStart: Boolean): Int = when {
            count == 0 -> 0
            atStart -> 1
            else -> index.coerceAtLeast(1)
        }
    }

    /**
     * 結束在指定位置的一頁，例如上一頁或最後一頁。
     *
     * @property index 最後一筆的 1-based 位置；最後一頁為 null，以回覆的總數為準。
     */
    data class EndsAt(val index: Int?) : ListPosition {
        override fun firstEntryIndex(count: Int, totalCount: Int, atStart: Boolean): Int = when {
            count == 0 -> 0
            atStart -> 1
            else -> ((index ?: totalCount) - count + 1).coerceAtLeast(1)
        }
    }
}
