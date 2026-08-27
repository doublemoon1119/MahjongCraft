package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * [TableState] 的完整權威 persistence DTO。
 *
 * @property id Game 與實體麻將桌共用的穩定 UUID。
 * @property players 所有玩家的完整權威狀態。
 * @property config 完整且帶穩定 type key 的規則配置。
 * @property tileWall 依原始順序保存的完整牌山。
 * @property prevalentWind 當前場風。
 * @property roundNumber 當前局數。
 * @property comboCount 當前連莊次數。
 * @property currentPlayerIndex 目前行動玩家的索引。
 * @property dynamicRuleState 規則專屬牌桌狀態；沒有狀態時為 null。
 * @property pendingReaction 尚未完成的捨牌反應視窗。
 * @property pendingKanReaction 尚未完成的搶槓反應視窗。
 * @property wallOpening 本局權威擲骰決定的牌牆開門位置；規則尚未支援開門流程時為 null。
 * @property initialDeadWall 開局瞬間的王牌快照；規則尚未支援開門流程時為空清單。
 * @property finishedPlayerIds 本局已完成、不再參與後續回合的玩家 Uuid 集合；舊存檔缺少此欄位時
 * 預設空集合。
 */
@Serializable
data class TableStatePersistenceDto(
    val id: String,
    val players: List<MahjongPlayerPersistenceDto>,
    val config: TypedPersistenceDto,
    val tileWall: TileWallPersistenceDto,
    val prevalentWind: WindPersistenceDto,
    val roundNumber: Int,
    val comboCount: Int,
    val currentPlayerIndex: Int,
    val dynamicRuleState: TypedPersistenceDto?,
    val pendingReaction: PendingReactionPersistenceDto?,
    val pendingKanReaction: PendingKanReactionPersistenceDto?,
    val wallOpening: WallOpeningPersistenceDto?,
    val initialDeadWall: List<IdentifiedTilePersistenceDto>,
    val finishedPlayerIds: Set<String> = emptySet(),
)

/** 將 [TableState] 轉換成完整權威 persistence DTO。 */
fun TableState.toPersistenceDto(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): TableStatePersistenceDto = TableStatePersistenceDto(
    id = id.toString(),
    players = players.map {
        it.toPersistenceDto(
            discardPileRegistry,
            playerRuleStateRegistry,
            exhaustiveDrawReasonRegistry,
            extensionGameActionRegistry,
            json,
        )
    },
    config = ruleConfigRegistry.encode(config, json),
    tileWall = tileWall.toPersistenceDto(),
    prevalentWind = WindPersistenceDto.valueOf(prevalentWind.name),
    roundNumber = roundNumber,
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.let { dynamicRuleStateRegistry.encode(it, json) },
    pendingReaction = pendingReaction?.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json),
    pendingKanReaction = pendingKanReaction?.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json),
    wallOpening = wallOpening?.toPersistenceDto(),
    initialDeadWall = initialDeadWall.map { it.toPersistenceDto() },
    finishedPlayerIds = finishedPlayerIds.map(Uuid::toString).toSet(),
)

/** 將 [TableStatePersistenceDto] 驗證並還原成完整權威 [TableState]。 */
fun TableStatePersistenceDto.toDomain(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    json: Json = Json,
): TableState = TableState(
    id = Uuid.parse(id),
    players = players.map {
        it.toDomain(
            discardPileRegistry,
            playerRuleStateRegistry,
            exhaustiveDrawReasonRegistry,
            extensionGameActionRegistry,
            json,
        )
    },
    config = ruleConfigRegistry.decode(config, json),
    tileWall = tileWall.toDomain(),
    prevalentWind = Wind.valueOf(prevalentWind.name),
    roundNumber = roundNumber,
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.let { dynamicRuleStateRegistry.decode(it, json) },
    pendingReaction = pendingReaction?.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json),
    pendingKanReaction = pendingKanReaction?.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, json),
    wallOpening = wallOpening?.toDomain(),
    initialDeadWall = initialDeadWall.map { it.toDomain() },
    finishedPlayerIds = finishedPlayerIds.map(Uuid::parse).toSet(),
)
