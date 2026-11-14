package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 將 [TableState] 轉換成完整權威 persistence DTO。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param componentRegistry 規則組件的保存轉換。
 * @return 包含原模型資料的持久化 DTO。
 */
fun TableState.toPersistenceDto(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    componentRegistry: ComponentPersistenceRegistry,
): TableStatePersistenceDto = TableStatePersistenceDto(
    id = id.toString(),
    components = componentRegistry.encode(components),
    players = players.map {
        it.toPersistenceDto(discardPileRegistry, exhaustiveDrawReasonRegistry, extensionGameActionRegistry, componentRegistry)
    },
    config = ruleConfigRegistry.encode(config),
    tileWall = tileWall.toPersistenceDto(componentRegistry),
    dealerPlayerId = dealerPlayerId.toString(),
    prevalentWind = WindPersistenceDto.valueOf(prevalentWind.name),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toPersistenceDto(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    turnPhase = turnPhase.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, componentRegistry),
    wallOpening = wallOpening?.toPersistenceDto(),
    openingDiceRolls = openingDiceRolls.map { it.toPersistenceDto() },
    reservedWallTiles = reservedWallTiles.map { it.toPersistenceDto(componentRegistry) },
    finishedPlayerIds = finishedPlayerIds.map(Uuid::toString).toSet(),
    physicalWallLayout = physicalWallLayout?.toPersistenceDto(),
    publicTileEvidenceIds = publicTileEvidenceIds.map(Uuid::toString).toSet(),
    revealedHandSettlements = revealedHandSettlements.map { settlement ->
        RevealedHandSettlementPersistenceDto(
            playerId = settlement.playerId.toString(),
            waitingTiles = settlement.waitingTiles.map { it.toPersistenceDto() },
        )
    },
    roundSerial = roundSerial,
    discardOrder = discardOrder.map(Uuid::toString),
)

/**
 * 將 [TableStatePersistenceDto] 驗證並還原成完整權威 [TableState]。
 *
 * @param ruleConfigRegistry 規則設定的持久化轉換註冊表。
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param componentRegistry 規則組件的保存轉換。
 * @return 還原的領域模型。
 */
fun TableStatePersistenceDto.toDomain(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    componentRegistry: ComponentPersistenceRegistry,
): TableState = TableState(
    id = Uuid.parse(id),
    components = componentRegistry.decode(components),
    players = players.map { it.toDomain(discardPileRegistry, exhaustiveDrawReasonRegistry, extensionGameActionRegistry, componentRegistry) },
    config = ruleConfigRegistry.decode(config),
    tileWall = tileWall.toDomain(componentRegistry),
    dealerPlayerId = Uuid.parse(dealerPlayerId),
    prevalentWind = Wind.valueOf(prevalentWind.name),
    roundNumber = roundNumber,
    roundPosition = roundPosition.toDomain(),
    comboCount = comboCount,
    currentPlayerIndex = currentPlayerIndex,
    turnPhase = turnPhase.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry, componentRegistry),
    wallOpening = wallOpening?.toDomain(),
    openingDiceRolls = openingDiceRolls.map { it.toDomain() },
    reservedWallTiles = reservedWallTiles.map { it.toDomain(componentRegistry) },
    finishedPlayerIds = finishedPlayerIds.map(Uuid::parse).toSet(),
    physicalWallLayout = physicalWallLayout?.toDomain(),
    publicTileEvidenceIds = publicTileEvidenceIds.map(Uuid::parse).toSet(),
    revealedHandSettlements = revealedHandSettlements.map { settlement ->
        RevealedHandSettlement(
            playerId = Uuid.parse(settlement.playerId),
            waitingTiles = settlement.waitingTiles.map { it.toDomain() }.toSet(),
        )
    },
    roundSerial = roundSerial,
    discardOrder = discardOrder.map(Uuid::parse),
)
