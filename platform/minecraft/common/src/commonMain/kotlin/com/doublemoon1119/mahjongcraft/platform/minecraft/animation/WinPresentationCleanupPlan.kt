package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import kotlin.uuid.Uuid

/**
 * 中途胡牌演出結束後，各張真實牌該收到什麼收尾動畫的純規劃結果。
 *
 * 蓋牌與恢復可見分開記錄，避免把公開副露蓋成牌背，或漏掉演出期間暫時隱藏的牌。
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
         * @return 蓋牌範圍取贏家的 [Hand.standingTiles]，不含副露；恢復可見的範圍沿用指定的隱藏牌集合。
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
