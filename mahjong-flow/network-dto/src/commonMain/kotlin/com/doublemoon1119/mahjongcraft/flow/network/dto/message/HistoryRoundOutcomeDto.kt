package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * Replay 局結算結果 DTO。
 *
 * @property reasonId 結算原因識別碼。
 * @property beneficiarySeats 受益玩家座位。
 * @property scoresBySeat 依座位索引排列的分數。
 * @property classification 局結算分類。
 * @property responsibleSeats 責任玩家座位。
 * @property transitionDirective 下一任莊家的類型 ID；整場完成事實為 null。
 * @property nextDealerSeat 規則指定的下一任莊家座位；其他類型為 null。
 * @property scoreChangesBySeat 該次結算相對於交易前的分數變化。
 * @property winnerDetails 各贏家的規則專屬詳情。
 * @property hasEarlierWinSettlement 是否已有較早的胡牌結算事實。
 */
@Serializable
data class HistoryRoundOutcomeDto(
    val reasonId: String,
    val beneficiarySeats: List<Int>,
    val scoresBySeat: Map<Int, Int>,
    val classification: String?,
    val responsibleSeats: List<Int>,
    val transitionDirective: String?,
    val nextDealerSeat: Int?,
    val scoreChangesBySeat: Map<Int, Int>,
    val winnerDetails: List<HistoryWinnerDetailsDto>,
    val hasEarlierWinSettlement: Boolean,
)
