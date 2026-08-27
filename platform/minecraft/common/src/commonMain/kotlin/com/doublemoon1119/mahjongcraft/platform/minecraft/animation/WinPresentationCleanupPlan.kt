package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import kotlin.uuid.Uuid

/**
 * 中途胡牌演出結束後，各張真實牌該收到什麼收尾動畫的純規劃結果。
 *
 * 抽成不依賴任何 Minecraft 型別的資料，讓「哪些牌該蓋、哪些牌該恢復可見」這條規則可以脫離 `World`／
 * `Entity` 單獨測試——搞錯範圍的後果是公開副露被蓋成牌背、或 showcase 借走的牌永遠隱形，兩者都是玩家
 * 直接看得到的破圖。
 *
 * @property concealTileIds 要蓋成牌背的牌。
 * @property restoreVisibleTileIds 要恢復可見的牌。
 */
data class WinPresentationCleanupPlan(
    val concealTileIds: Set<Uuid>,
    val restoreVisibleTileIds: Set<Uuid>,
) {
    /** 這次收尾總共會被排入動畫的牌，供呼叫端一次建立「不阻塞全桌」豁免。 */
    val animatedTileIds: Set<Uuid> get() = concealTileIds + restoreVisibleTileIds

    companion object {
        /**
         * 規劃收尾動畫。
         *
         * @param winnerHands 本次胡牌的贏家手牌。
         * @param showcaseHiddenTileIds 役滿 showcase **實際**交接給舞台代理、因而被設成隱形的真實牌；
         * 沒播 showcase 時傳空集合。恢復可見刻意只針對這一組，不拿手牌全集去猜。
         * @return 蓋牌範圍只取 [Hand.standingTiles]，**絕不包含副露**：吃、碰、明槓、暗槓本來就是公開
         * 資訊，蓋起來既不合規則，也會摧毀既有的牌面、橫置方向與加槓疊牌版面。榮和的胡牌張留在放銃者
         * 的牌河、不屬於贏家立牌，因此只會出現在恢復可見那一組。
         */
        fun of(
            winnerHands: Collection<Hand>,
            showcaseHiddenTileIds: Set<Uuid>,
        ): WinPresentationCleanupPlan = WinPresentationCleanupPlan(
            concealTileIds = winnerHands.flatMapTo(mutableSetOf()) { hand -> hand.standingTiles.map { it.id } },
            restoreVisibleTileIds = showcaseHiddenTileIds,
        )
    }
}
