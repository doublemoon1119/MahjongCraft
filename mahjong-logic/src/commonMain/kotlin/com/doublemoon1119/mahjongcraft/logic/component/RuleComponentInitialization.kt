package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializationResult
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileArea
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileAreas

/**
 * 在正式開局或換局時，依登記規格建立組件；各定義都讀取同一份候選桌況。
 *
 * 玩家與對局逐項重建、保留或轉換上一局資料。新牌張保留牌組定義給定的特性，再合併初始組件。
 * 開局時已存在的捨牌紀錄與副露和動作新建的紀錄一樣套用初值並驗證。
 * 不在快照查詢或存檔載入時呼叫，也不執行任何遊戲動作。
 *
 * @param ruleModuleId 本局規則的完整識別碼。
 * @param registry 本次執行已完成登記的組件規格。
 * @param previousTableState 上一局結束時的桌況；第一局為 null。
 * @return 包含已驗證組件的開局結果，牌張身分與牌牆位置保持不變。
 */
fun GameInitializationResult.initializeRuleComponents(
    ruleModuleId: String,
    registry: ComponentRegistry,
    previousTableState: TableState? = null,
): GameInitializationResult {
    val candidate = tableState.copy(
        components = ComponentSet.EMPTY,
        players = tableState.players.map { it.copy(components = ComponentSet.EMPTY) },
    )
    val tableContext = ComponentLifecycleContext(
        ruleModuleId = ruleModuleId,
        tableState = candidate,
        ownerType = BuiltInComponentOwners.TABLE,
        owner = candidate,
        previousTableState = previousTableState,
    )
    val tableComponents = if (previousTableState == null) {
        registry.initialize(tableContext)
    } else {
        registry.transitionRound(tableContext, previousTableState.components)
    }
    val tiles = candidate.resolveTileAreas().associate { area ->
        val tile = area.tile
        registry.validate(tile.components)
        val initial = registry.initialize(
            context = ComponentLifecycleContext(
                ruleModuleId = ruleModuleId,
                tableState = candidate,
                ownerType = BuiltInComponentOwners.TILE,
                owner = tile,
                previousTableState = previousTableState,
            ),
        )
        val values = initial.merge(tile.components)
        tile.id to if (values == tile.components) tile else tile.copy(components = values)
    }
    val previousPlayers = previousTableState?.players?.associateBy { it.id }
    val players = candidate.players.map { player ->
        val context = ComponentLifecycleContext(
            ruleModuleId = ruleModuleId,
            tableState = candidate,
            ownerType = BuiltInComponentOwners.PLAYER,
            owner = player,
            previousTableState = previousTableState,
        )
        val values = if (previousPlayers == null) {
            registry.initialize(context)
        } else {
            val previous = requireNotNull(previousPlayers[player.id]) { "Round transition cannot introduce an unknown player" }
            registry.transitionRound(context, previous.components)
        }
        val hand = player.hand
        var discardPile = player.discardPile
        player.discardPile.entries.forEachIndexed { index, entry ->
            val entryComponents = registry.initializeRecordComponents(
                context = ComponentLifecycleContext(
                    ruleModuleId = ruleModuleId,
                    tableState = candidate,
                    ownerType = BuiltInComponentOwners.DISCARD_ENTRY,
                    owner = entry,
                    previousTableState = previousTableState,
                ),
                written = entry.components,
            )
            if (entryComponents != entry.components) discardPile = discardPile.replaceEntryComponents(index, entryComponents)
            if (!entry.isTaken) {
                val replacement = tiles.getValue(entry.tile.id)
                if (replacement != entry.tile) discardPile = discardPile.replaceTile(replacement)
            }
        }
        player.copy(
            components = values,
            discardPile = discardPile,
            hand = Hand(
                tiles = hand.tiles.map { tiles.getValue(it.id) },
                lastDrawn = hand.lastDrawn?.let { tiles.getValue(it.id) },
                melds = hand.melds.map { meld ->
                    meld.copy(
                        components = registry.initializeRecordComponents(
                            context = ComponentLifecycleContext(
                                ruleModuleId = ruleModuleId,
                                tableState = candidate,
                                ownerType = BuiltInComponentOwners.MELD,
                                owner = meld,
                                previousTableState = previousTableState,
                            ),
                            written = meld.components,
                        ),
                        tiles = meld.tiles.map { tiles.getValue(it.id) },
                        sourceTile = meld.sourceTile?.let { tiles.getValue(it.id) },
                    )
                },
            ),
            tileAreas = PlayerTileAreas(
                areas = player.tileAreas.areas.map { area ->
                    PlayerTileArea(area.id, area.tiles.map { tiles.getValue(it.id) })
                },
            ),
        )
    }
    return copy(
        tableState = candidate.copy(
            components = tableComponents,
            players = players,
            tileWall = TileWall(candidate.tileWall.getAllTiles().map { tiles.getValue(it.id) }),
            reservedWallTiles = candidate.reservedWallTiles.map { tiles.getValue(it.id) },
        ),
    )
}
