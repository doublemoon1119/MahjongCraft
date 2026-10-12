package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import kotlin.uuid.Uuid

/**
 * AI 對一個局面中各玩家的估計：預估打點、捨牌危險度與聽牌可能性。
 *
 * 這些都是估計而不是規則事實；需要規則事實（例如不能榮和的牌、依牌張加計的打點）時，實作從規則模組的 [PositionRules] 取得。
 * 所有方法只讀取 [PositionView]，不可能使用評估者看不到的資訊。
 */
interface OpponentModel {
    /** 推測對手手牌的深度。 */
    val readingDepth: ReadingDepth

    /**
     * [playerId] 一手和牌的預估基準點數，用於無法具體估算手牌價值時；[playerId] 可以是評估者本人。
     *
     * @param view 評估所使用、以評估者為中心的局面視角。
     * @param playerId 要估算和牌點數的玩家。
     * @return 該玩家一手和牌的預估點數。
     */
    fun baselineWinValue(view: PositionView, playerId: Uuid): Int

    /**
     * 評估者打出 [tile] 時，被已經聽牌的 [opponentId] 榮和的可能性，介於 0 到 1。
     *
     * @param view 評估所使用、以評估者為中心的局面視角。
     * @param opponentId 可能榮和的對手。
     * @param tile 評估者考慮打出的牌。
     * @return 對手榮和這張牌的估計機率。
     */
    fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double

    /**
     * 從公開資訊估計 [opponentId] 的威脅。
     *
     * @param view 評估所使用、以評估者為中心的局面視角。
     * @param opponentId 要估計威脅的對手。
     * @return 該對手的聽牌機率與和牌點數估計。
     */
    fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate
}
