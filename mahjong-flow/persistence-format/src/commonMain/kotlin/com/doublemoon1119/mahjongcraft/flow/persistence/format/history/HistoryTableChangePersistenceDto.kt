package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.PendingReactionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.PendingRobbingReactionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RevealedHandSettlementPersistenceDto
import kotlinx.serialization.Serializable

/**
 * 一筆權威交易中可重建的桌況差異；未列出的欄位保持原值。
 *
 * @property changedPlayers 實際變動的玩家狀態及其動作紀錄增量。
 * @property wall 活牌牆兩端移除數量；未改變時為 null。
 * @property currentPlayerIndex 新的行動玩家索引；未改變時為 null。
 * @property dynamicRuleState 規則動態狀態的新值；外層 null 代表未改變。
 * @property pendingReaction 捨牌反應視窗的新值；外層 null 代表未改變。
 * @property pendingRobbingReaction 搶和反應視窗的新值；外層 null 代表未改變。
 * @property reservedWallTiles 規則保留牌的新順序；未改變時為 null。
 * @property physicalWallLayout 實體牌牆格位的局部變化；未改變時為 null。
 * @property finishedPlayerIds 本局已完成玩家的 UUID 字串集合；未改變時為 null。
 * @property revealedHandTileIds 本局已公開手牌的 UUID 字串集合；未改變時為 null。
 * @property revealedHandSettlements 流局公開手牌與等待牌的新資料；未改變時為 null，空集合代表清空。
 */
@Serializable
data class HistoryTableChangePersistenceDto(
    val changedPlayers: List<HistoryPlayerChangePersistenceDto>,
    val wall: HistoryWallChangePersistenceDto?,
    val currentPlayerIndex: Int?,
    val dynamicRuleState: HistoryChangedValuePersistenceDto<TypedPersistenceDto?>?,
    val pendingReaction: HistoryChangedValuePersistenceDto<PendingReactionPersistenceDto?>?,
    val pendingRobbingReaction: HistoryChangedValuePersistenceDto<PendingRobbingReactionPersistenceDto?>?,
    val reservedWallTiles: List<IdentifiedTilePersistenceDto>?,
    val physicalWallLayout: HistoryWallLayoutChangePersistenceDto?,
    val finishedPlayerIds: Set<String>?,
    val revealedHandTileIds: Set<String>?,
    val revealedHandSettlements: List<RevealedHandSettlementPersistenceDto>?,
)
