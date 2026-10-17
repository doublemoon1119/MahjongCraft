package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.serialization.Serializable

/**
 * [TableState] 的完整權威 persistence DTO。
 *
 * @property id Game 與場地共用的穩定 UUID。
 * @property players 所有玩家的完整權威狀態。
 * @property config 完整且帶穩定 type key 的規則配置。
 * @property tileWall 依原始順序保存的完整牌山。
 * @property dealerPlayerId 本局權威莊家 Uuid。
 * @property prevalentWind 當前場風。
 * @property roundNumber 當前局數。
 * @property roundPosition 當前權威局位。
 * @property comboCount 當前連莊次數。
 * @property currentPlayerIndex 目前行動玩家的索引。
 * @property dynamicRuleState 規則專屬的動態對局狀態；沒有狀態時為 null。
 * @property pendingReaction 尚未完成的捨牌反應視窗。
 * @property pendingRobbingReaction 尚未完成的搶和反應視窗。
 * @property wallOpening 本局權威擲骰決定的牌牆開門位置；沒有時為 null。
 * @property reservedWallTiles 目前規則保留的牌張。
 * @property finishedPlayerIds 本局已完成玩家的 UUID 集合。
 * @property physicalWallLayout 仍在牌牆中的牌張實體位置；沒有時為 null。
 * @property revealedHandTileIds 本局已公開的手牌 UUID 集合。
 * @property revealedHandSettlements 本局流局 resolver 計算出的公開手牌與等待牌資料。
 */
@Serializable
data class TableStatePersistenceDto(
    val id: String,
    val players: List<MahjongPlayerPersistenceDto>,
    val config: TypedPersistenceDto,
    val tileWall: TileWallPersistenceDto,
    val dealerPlayerId: String,
    val prevalentWind: WindPersistenceDto,
    val roundNumber: Int,
    val roundPosition: MatchRoundPositionPersistenceDto,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: TypedPersistenceDto?,
    val pendingReaction: PendingReactionPersistenceDto?,
    val pendingRobbingReaction: PendingRobbingReactionPersistenceDto?,
    val wallOpening: WallOpeningPersistenceDto?,
    val reservedWallTiles: List<IdentifiedTilePersistenceDto>,
    val finishedPlayerIds: Set<String>,
    val physicalWallLayout: TileWallPhysicalLayoutPersistenceDto?,
    val revealedHandTileIds: Set<String>,
    val revealedHandSettlements: List<RevealedHandSettlementPersistenceDto>,
)
