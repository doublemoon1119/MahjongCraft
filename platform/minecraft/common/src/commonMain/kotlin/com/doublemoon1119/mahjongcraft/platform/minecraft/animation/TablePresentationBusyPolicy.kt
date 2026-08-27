package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

/**
 * 決定「一張正在播放動畫的牌，該不該讓整桌進入忙碌狀態」的純規則。
 *
 * 抽成不依賴任何 Minecraft 型別的獨立物件，讓這條判斷可以脫離 `World`／`Entity` 單獨測試——牌桌忙碌
 * 判定會擋住玩家輸入、AI、強制自動操作與決策計時器，判斷錯誤的後果是整桌卡住或該擋的沒擋，值得有
 * 直接的測試覆蓋。
 */
object TablePresentationBusyPolicy {
    /**
     * 這張牌目前的動畫是否應該讓整桌進入忙碌狀態。
     *
     * @param isAnimating 這張牌是否正在播放動畫。
     * @param nonBlockingUntilGameTime 「動畫不阻塞全桌」豁免的絕對到期 game time；`0` 代表沒有豁免。
     * 中途胡牌（本局在胡牌後仍繼續）會替已完成玩家的真實牌建立這個豁免，讓理牌／倒牌／隱形／恢復
     * 顯示／蓋牌這一串動畫不擋住其他仍在本局中的玩家。
     * @param currentGameTime 目前的絕對 game time。
     * @return 是否應該讓整桌忙碌。沒有豁免的動畫（發牌、摸牌、捨牌等）一律讓整桌忙碌。
     */
    fun tileBlocksTableBusy(
        isAnimating: Boolean,
        nonBlockingUntilGameTime: Long,
        currentGameTime: Long,
    ): Boolean = isAnimating && currentGameTime >= nonBlockingUntilGameTime
}
