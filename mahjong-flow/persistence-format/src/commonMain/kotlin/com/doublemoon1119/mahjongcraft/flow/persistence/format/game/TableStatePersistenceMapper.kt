package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 將 [TableState] 轉換成完整權威 persistence DTO。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param playerRuleStateRegistry 玩家規則狀態的持久化轉換註冊表。
 * @param dynamicRuleStateRegistry 動態規則狀態的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 包含原模型資料的持久化 DTO。
 */
fun TableState.toPersistenceDto(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): TableStatePersistenceDto = TableStatePersistenceDto(
    id = id.toString(),
    players = players.map {
        it.toPersistenceDto(discardPileRegistry, playerRuleStateRegistry, exhaustiveDrawReasonRegistry, extensionGameActionRegistry)
    },
    config = ruleConfigRegistry.encode(config),
    tileWall = tileWall.toPersistenceDto(),
    dealerPlayerId = dealerPlayerId.toString(),
    prevalentWind = WindPersistenceDto.valueOf(prevalentWind.name),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toPersistenceDto(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.let { dynamicRuleStateRegistry.encode(it) },
    pendingReaction = pendingReaction?.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    pendingRobbingReaction = pendingRobbingReaction?.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    wallOpening = wallOpening?.toPersistenceDto(),
    reservedWallTiles = reservedWallTiles.map { it.toPersistenceDto() },
    finishedPlayerIds = finishedPlayerIds.map(Uuid::toString).toSet(),
    physicalWallLayout = physicalWallLayout?.toPersistenceDto(),
    revealedHandTileIds = revealedHandTileIds.map(Uuid::toString).toSet(),
)

/**
 * 將 [TableStatePersistenceDto] 驗證並還原成完整權威 [TableState]。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param playerRuleStateRegistry 玩家規則狀態的持久化轉換註冊表。
 * @param dynamicRuleStateRegistry 動態規則狀態的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 還原的領域模型。
 */
fun TableStatePersistenceDto.toDomain(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): TableState = TableState(
    id = Uuid.parse(id),
    players = players.map { it.toDomain(discardPileRegistry, playerRuleStateRegistry, exhaustiveDrawReasonRegistry, extensionGameActionRegistry) },
    config = ruleConfigRegistry.decode(config),
    tileWall = tileWall.toDomain(),
    dealerPlayerId = Uuid.parse(dealerPlayerId),
    prevalentWind = Wind.valueOf(prevalentWind.name),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toDomain(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    dynamicRuleState = dynamicRuleState?.let { dynamicRuleStateRegistry.decode(it) },
    pendingReaction = pendingReaction?.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    pendingRobbingReaction = pendingRobbingReaction?.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry),
    wallOpening = wallOpening?.toDomain(),
    reservedWallTiles = reservedWallTiles.map { it.toDomain() },
    finishedPlayerIds = finishedPlayerIds.map(Uuid::parse).toSet(),
    physicalWallLayout = physicalWallLayout?.toDomain(),
    revealedHandTileIds = revealedHandTileIds.map(Uuid::parse).toSet(),
)
