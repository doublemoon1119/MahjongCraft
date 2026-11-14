package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileArea
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 將 [MahjongPlayer] 轉換成 persistence DTO。
 *
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param componentRegistry 規則組件的保存轉換。
 * @return 包含原模型資料的持久化 DTO。
 */
fun MahjongPlayer.toPersistenceDto(
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    componentRegistry: ComponentPersistenceRegistry,
): MahjongPlayerPersistenceDto = MahjongPlayerPersistenceDto(
    id = id.toString(),
    components = componentRegistry.encode(components),
    tileAreas = tileAreas.areas.map { area ->
        PlayerTileAreaPersistenceDto(
            id = area.id,
            tiles = area.tiles.map { it.toPersistenceDto(componentRegistry) },
        )
    },
    initialSeatIndex = initialSeatIndex,
    hand = hand.toPersistenceDto(componentRegistry),
    discardPile = discardPileRegistry.encode(discardPile),
    score = score,
    seatWind = WindPersistenceDto.valueOf(seatWind.name),
    passedTilesInRound = passedTilesInRound.map { it.toPersistenceDto() },
    actionHistory = actionHistory.map { it.toPersistenceDto(exhaustiveDrawReasonRegistry, extensionGameActionRegistry) },
)

/**
 * 將 [MahjongPlayerPersistenceDto] 還原成 [MahjongPlayer]。
 *
 * @param discardPileRegistry 牌河的持久化轉換註冊表。
 * @param exhaustiveDrawReasonRegistry 流局原因的持久化轉換註冊表。
 * @param extensionGameActionRegistry 擴充遊戲動作的持久化轉換註冊表。
 * @param componentRegistry 規則組件的保存轉換。
 * @return 還原的領域模型。
 */
fun MahjongPlayerPersistenceDto.toDomain(
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    extensionGameActionRegistry: PersistenceDtoRegistry<ExtensionGameAction>,
    componentRegistry: ComponentPersistenceRegistry,
): MahjongPlayer = MahjongPlayer(
    id = Uuid.parse(id),
    components = componentRegistry.decode(components),
    tileAreas = PlayerTileAreas(
        areas = tileAreas.map { area ->
            PlayerTileArea(
                id = area.id,
                tiles = area.tiles.map { it.toDomain(componentRegistry) },
            )
        },
    ),
    initialSeatIndex = initialSeatIndex,
    hand = hand.toDomain(componentRegistry),
    discardPile = discardPileRegistry.decode(discardPile),
    score = score,
    seatWind = Wind.valueOf(seatWind.name),
    passedTilesInRound = passedTilesInRound.mapTo(mutableSetOf()) { it.toDomain() },
    actionHistory = actionHistory.map { it.toDomain(exhaustiveDrawReasonRegistry, extensionGameActionRegistry) },
)
