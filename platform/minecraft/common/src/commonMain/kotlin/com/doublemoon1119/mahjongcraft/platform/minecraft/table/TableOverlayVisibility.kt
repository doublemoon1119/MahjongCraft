package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 依保存的隱藏期限判斷常駐資訊面板是否顯示，不依賴動畫步驟是否已執行。 */
object TableOverlayVisibility {
    /**
     * 判斷指定時間是否仍須隱藏。
     *
     * @param hiddenUntilGameTime 隱藏截止時間；最小值表示未隱藏，最大值表示隱藏到移除。
     * @param gameTime 目前的遊戲時間。
     * @return 尚未到期或設定為隱藏到移除時為 true。
     */
    fun isHidden(hiddenUntilGameTime: Long, gameTime: Long): Boolean = hiddenUntilGameTime == Long.MAX_VALUE || gameTime < hiddenUntilGameTime

    /**
     * 延長隱藏期限，不讓較短的新期限提早解除隱藏。
     *
     * @param currentUntil 目前的截止時間。
     * @param requestedUntil 要求的截止時間。
     * @return 兩個時間中較晚的值。
     */
    fun extend(currentUntil: Long, requestedUntil: Long): Long = maxOf(currentUntil, requestedUntil)
}
