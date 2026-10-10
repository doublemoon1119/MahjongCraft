package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import kotlinx.serialization.Serializable

/**
 * 單局事件資料 DTO。
 *
 * @property identity 對局與玩家識別資料。
 * @property roundNumber 局序號。
 * @property transactions 有界交易序列。
 * @property nextTransactionIndex 下一頁起點。
 * @property tileCatalog 回覆末端已宣告的牌目錄。
 */
@Serializable
data class HistoryRoundEventsDto(
    val identity: HistoryReplayIdentityDto,
    val roundNumber: Int,
    val transactions: List<HistoryReplayTransactionDto>,
    val nextTransactionIndex: Int?,
    val tileCatalog: List<TileDto>,
)
