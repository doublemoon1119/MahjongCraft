package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import kotlin.uuid.Uuid

/**
 * 已解析的胡牌公開詳情快照；只保存規則中立的語意欄位，不保存規則領域物件。
 *
 * @property playerId 胡牌玩家 UUID。
 * @property detailFields 已解析的翻符、役種或其他規則擴充欄位。
 * @property hand 結算時的立牌順序及和牌張；未記錄時為 null。
 */
data class HistoryWinDetails(
    val playerId: Uuid,
    val detailFields: List<WinSettlementDetailField>,
    val hand: HistoryWinningHand? = null,
) {
    init {
        require(detailFields.map(WinSettlementDetailField::id).distinct().size == detailFields.size)
    }
}
