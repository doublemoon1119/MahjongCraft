package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DynamicRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.serialization.Serializable

/**
 * [TableStateSnapshot] 的 observer-specific 網路 DTO。
 *
 * @property id 對局 UUID 字串。
 * @property players 玩家快照。
 * @property config 本局規則設定。
 * @property tileWall 牌牆快照。
 * @property dealerPlayerId 莊家玩家 UUID 字串。
 * @property prevalentWind 場風。
 * @property roundNumber 跨場風累計、從一開始計算的局數。
 * @property roundPosition 場次位置。
 * @property comboCount 本場數。
 * @property currentPlayerIndex 目前玩家的座位索引。
 * @property dynamicRuleState 規則專屬動態狀態；沒有時為 `null`。
 * @property finishedPlayerIds 已完成本局玩家 UUID 字串。
 * @property physicalWallLayout 實體牌牆布局；沒有時為 `null`。
 * @property openingDiceRolls 本局開局程序已完成的逐輪擲骰結果。
 * @property revealedHandTiles 對 observer 公開的額外手牌牌張。
 */
@Serializable
data class TableStateSnapshotDto(
    val id: String,
    val players: List<MahjongPlayerSnapshotDto>,
    val config: MahjongRuleConfigDto,
    val tileWall: TileWallSnapshotDto,
    val dealerPlayerId: String,
    val prevalentWind: WindDto,
    val roundNumber: Int,
    val roundPosition: MatchRoundPositionDto,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: DynamicRuleStateDto?,
    val finishedPlayerIds: Set<String>,
    val physicalWallLayout: TileWallPhysicalLayoutDto?,
    val openingDiceRolls: List<OpeningDiceRollDto>,
    val revealedHandTiles: List<IdentifiedTileDto>,
)
