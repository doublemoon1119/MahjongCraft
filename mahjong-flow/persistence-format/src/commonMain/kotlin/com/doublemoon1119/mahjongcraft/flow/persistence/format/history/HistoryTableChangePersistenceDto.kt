package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.OpeningDiceRollPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RevealedHandSettlementPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TurnPhasePersistenceDto
import kotlinx.serialization.Serializable

/**
 * 一筆權威交易中可重建的桌況差異；未列出的欄位保持原值。
 *
 * @property components 對局組件的新集合；未改變時為 null，空集合代表清空。
 * @property changedPlayers 實際變動的玩家狀態及其動作紀錄增量。
 * @property wall 活牌牆兩端移除數量；未改變時為 null。
 * @property currentPlayerIndex 新的行動玩家索引；未改變時為 null。
 * @property turnPhase 新的回合階段；未改變時為 null。
 * @property reservedWallTiles 規則保留牌的新順序；未改變時為 null。
 * @property physicalWallLayout 實體牌牆格位的局部變化；未改變時為 null。
 * @property finishedPlayerIds 本局已完成玩家的 UUID 字串集合；未改變時為 null。
 * @property publicTileEvidenceIds 本局公開證據的 UUID 字串集合；未改變時為 null。
 * @property revealedHandSettlements 流局公開手牌與等待牌的新資料；未改變時為 null，空集合代表清空。
 * @property openingDiceRolls 本局逐輪擲骰結果；未改變時為 null。
 * @property lastDiscardTileId 本局最後打出的牌的 UUID 字串；未改變時為 null。
 */
@Serializable
data class HistoryTableChangePersistenceDto(
    val components: List<TypedPersistenceDto>?,
    val changedPlayers: List<HistoryPlayerChangePersistenceDto>,
    val wall: HistoryWallChangePersistenceDto?,
    val currentPlayerIndex: Int?,
    val turnPhase: TurnPhasePersistenceDto?,
    val reservedWallTiles: List<IdentifiedTilePersistenceDto>?,
    val physicalWallLayout: HistoryWallLayoutChangePersistenceDto?,
    val finishedPlayerIds: Set<String>?,
    val publicTileEvidenceIds: Set<String>?,
    val revealedHandSettlements: List<RevealedHandSettlementPersistenceDto>?,
    val openingDiceRolls: List<OpeningDiceRollPersistenceDto>?,
    val lastDiscardTileId: String?,
)
