package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 將 [MahjongPlayer] 轉換成 persistence DTO。
 *
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param playerRuleStateRegistry 玩家規則狀態的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 包含原模型資料的持久化 DTO。
 */
fun MahjongPlayer.toPersistenceDto(
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): MahjongPlayerPersistenceDto = MahjongPlayerPersistenceDto(
    id = id.toString(),
    initialSeatIndex = initialSeatIndex,
    hand = hand.toPersistenceDto(),
    discardPile = discardPileRegistry.encode(discardPile),
    playerRuleState = playerRuleState?.let { playerRuleStateRegistry.encode(it) },
    score = score,
    seatWind = WindPersistenceDto.valueOf(seatWind.name),
    passedTilesInRound = passedTilesInRound.map { it.toPersistenceDto() },
    actionHistory = actionHistory.map { it.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry) },
)

/**
 * 將 [MahjongPlayerPersistenceDto] 還原成 [MahjongPlayer]。
 *
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param playerRuleStateRegistry 玩家規則狀態的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @return 還原的領域模型。
 */
fun MahjongPlayerPersistenceDto.toDomain(
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
): MahjongPlayer = MahjongPlayer(
    id = Uuid.parse(id),
    initialSeatIndex = initialSeatIndex,
    hand = hand.toDomain(),
    discardPile = discardPileRegistry.decode(discardPile),
    playerRuleState = playerRuleState?.let { playerRuleStateRegistry.decode(it) },
    score = score,
    seatWind = Wind.valueOf(seatWind.name),
    passedTilesInRound = passedTilesInRound.mapTo(mutableSetOf()) { it.toDomain() },
    actionHistory = actionHistory.map { it.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry) },
)
