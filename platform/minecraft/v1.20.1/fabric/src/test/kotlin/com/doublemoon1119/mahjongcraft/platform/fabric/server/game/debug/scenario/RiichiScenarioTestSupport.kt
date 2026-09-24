package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 座位設定情境測試共用的桌況查詢。
 *
 * 這些查詢都不改動權威狀態：需要「如果摸到／被打出這張牌會怎樣」時，只複製一份玩家交給
 * [LegalActionValidator]，桌況本身維持情境載入後的樣子。
 */
internal val scenarioModuleRegistry: MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
    registerBuiltInRuleModules()
    freeze()
}

/** 建立呼叫者以外有 [aiOpponentCount] 位 AI 的正式日麻對局。 */
internal fun createRiichiScenarioContext(
    aiOpponentCount: Int = 3,
    config: RiichiRuleConfig = RiichiRuleConfig(),
): DebugGameScenarioContext {
    val playerIds = List(4) { Uuid.random() }
    val initialized = GameInitializer.initialize(
        id = Uuid.random(),
        playerIds = playerIds,
        module = RiichiRuleModule("mahjongcraft:riichi", config),
        aiPlayerStrategyKeys = playerIds.drop(1).take(aiOpponentCount).associateWith { "random" },
    )
    val game = Game(
        tableState = initialized.tableState,
        flowConfig = GameFlowConfig(),
        hostId = playerIds.first(),
        roomPlayerIds = playerIds,
    )
    return DebugGameScenarioContext(game, playerIds.first())
}

/** 以呼叫者為 0，依打牌順序取得座位。 */
internal fun TableState.seat(offset: Int): MahjongPlayer = players[(currentPlayerIndex + offset) % players.size]

/** 呼叫者打出剛摸到的牌後、他家行動時的手牌狀態。 */
internal fun TableState.waitingSeat(offset: Int): MahjongPlayer = seat(offset).let { player ->
    player.copy(hand = player.hand.copy(lastDrawn = null))
}

internal fun TableState.scenarioValidator(): LegalActionValidator = scenarioModuleRegistry.getModule(config).createLegalActionValidator()

internal fun TableState.liveWallTile(index: Int): IdentifiedTile = tileWall.getAllTiles()[index]

internal fun TableState.liveWallFront(count: Int): List<Tile> = tileWall.getAllTiles().take(count).map { it.tile }

/** [offset] 座位對 [discarderOffset] 座位打出的一張 [tile] 可做的反應。 */
internal fun TableState.reactionsOf(
    offset: Int,
    discarderOffset: Int,
    tile: IdentifiedTile,
): List<GameAction> = scenarioValidator().getLegalActions(
    tableState = this,
    player = waitingSeat(offset),
    sourceAction = GameAction.Discard(tile.id),
    sourceDirection = relativeDirectionOf(seat(offset).id, seat(discarderOffset).id),
    incomingTile = tile,
)

internal fun TableState.canRon(
    offset: Int,
    discarderOffset: Int,
    tile: Tile,
): Boolean = reactionsOf(offset, discarderOffset, IdentifiedTile(Uuid.random(), tile)).any { it is GameAction.Ron }

/** [offset] 座位摸到 [drawnTile] 時，自己回合可宣告的特殊動作。 */
internal fun TableState.ownTurnActions(
    offset: Int,
    drawnTile: IdentifiedTile,
): List<GameAction> = scenarioValidator().getLegalActions(
    tableState = this,
    player = waitingSeat(offset),
    sourceAction = GameAction.Draw,
    sourceDirection = RelativeDirection.Self,
    incomingTile = drawnTile,
)

/** 情境載入當下，呼叫者以手上剛摸到的牌可宣告的特殊動作。 */
internal fun TableState.currentOwnTurnActions(): List<GameAction> = scenarioValidator().getLegalActions(
    tableState = this,
    player = currentPlayer,
    sourceAction = GameAction.Draw,
    sourceDirection = RelativeDirection.Self,
)

/** [offset] 座位摸到活牌第一張後，自己回合可宣告的槓。 */
internal fun TableState.ownTurnKans(offset: Int): List<GameAction.Kan> {
    val player = seat(offset).let { it.copy(hand = it.hand.copy(lastDrawn = liveWallTile(0))) }
    return scenarioValidator().getLegalActions(
        tableState = this,
        player = player,
        sourceAction = GameAction.Draw,
        sourceDirection = RelativeDirection.Self,
    ).filterIsInstance<GameAction.Kan>()
}

/** [offset] 座位能否搶下家以活牌第一張宣告的槓。 */
internal fun TableState.canRobKan(offset: Int, type: GameAction.KanType): Boolean {
    val kanTile = liveWallTile(0)
    return scenarioValidator().getLegalActions(
        tableState = this,
        player = waitingSeat(offset),
        sourceAction = GameAction.Kan(type, kanTile.id, emptyList()),
        sourceDirection = relativeDirectionOf(seat(offset).id, seat(1).id),
        incomingTile = kanTile,
    ).any { it is GameAction.Ron }
}
