package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import kotlin.uuid.Uuid

/**
 * 一位贏家的權威胡牌詳情快照。
 *
 * @property playerId 贏家玩家 ID。
 * @property seatIndex 贏家座位序。
 * @property responsiblePlayerId 放銃／被搶槓玩家；自摸或不歸咎特定玩家的特殊結算（例如流局滿貫）為 `null`。
 * @property totalScore 這位贏家本次胡牌獲得的總點數。
 * @property standingTileIds 立牌 ID，依規則的牌序排列，**不含**副露牌；副露牌另外完整列在 [melds]，兩者不得重複。
 * @property melds 已公開的副露。
 * @property winningTileId 胡牌張；特殊結算（同上）沒有胡牌張時為 `null`。
 * @property detailFields 規則專屬的胡牌詳情（例如日麻的翻符、役種），欄位 id 不得重複。
 */
data class WinSettlementWinnerPresentation(
    val playerId: Uuid,
    val seatIndex: Int,
    val responsiblePlayerId: Uuid?,
    val totalScore: Int,
    val standingTileIds: List<Uuid>,
    val melds: List<MeldPresentation>,
    val winningTileId: Uuid?,
    val detailFields: List<WinSettlementDetailField>,
) {
    init {
        require(detailFields.map(WinSettlementDetailField::id).distinct().size == detailFields.size)
    }
}
