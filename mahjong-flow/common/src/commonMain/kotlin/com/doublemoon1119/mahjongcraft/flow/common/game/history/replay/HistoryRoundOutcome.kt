package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective

/**
 * 歷史局結算結果。
 *
 * @property reasonId 結算原因識別碼。
 * @property beneficiarySeats 保存的受益玩家座位，不假設結果必為和牌。
 * @property scoresBySeat 依座位索引排列的分數。
 * @property scoreChangesBySeat 該次結算相對於交易前的分數變化；讀取器依交易前後分數補上，補上前為空。
 * @property winnerDetails 各贏家保存的規則專屬詳情；沒有胡牌詳情的結算（例如流局）為空。
 * @property hasEarlierWinSettlement 是否已有較早的胡牌結算事實。
 * @property classification 保存的局結算分類；整場完成事實不含此資料。
 * @property responsibleSeats 保存的責任玩家座位；整場完成事實不含此資料。
 * @property transitionDirective 保存的莊家推進決策；整場完成事實不含此資料。
 */
data class HistoryRoundOutcome(
    val reasonId: String,
    val beneficiarySeats: List<Int>,
    val scoresBySeat: Map<Int, Int>,
    val classification: RoundCompletionClassification? = null,
    val responsibleSeats: List<Int> = emptyList(),
    val transitionDirective: RoundTransitionDirective? = null,
    val scoreChangesBySeat: Map<Int, Int> = emptyMap(),
    val winnerDetails: List<HistoryWinnerDetails> = emptyList(),
    val hasEarlierWinSettlement: Boolean = false,
)
