package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlin.uuid.Uuid

/**
 * 情境測試共用的內建規則模組登錄表；建立後即凍結，供合法動作查詢使用。
 */
internal val scenarioModuleRegistry: MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
    registerBundledRuleModules()
    freeze()
}

/**
 * 建立呼叫者以外有 [aiOpponentCount] 位 AI 的正式日麻對局。
 *
 * @param aiOpponentCount 由 AI 操控的對手數量。
 * @param config 要使用的日麻規則設定。
 * @return 以第一位玩家為呼叫者的情境輸入。
 */
internal fun createRiichiScenarioContext(
    aiOpponentCount: Int = 3,
    config: RiichiRuleConfig = RiichiRuleConfig(),
): DebugGameScenarioContext = createScenarioContext(
    module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config),
    playerCount = 4,
    aiOpponentCount = aiOpponentCount,
)

/**
 * 建立呼叫者以外兩位都是 AI 的正式三人日麻對局。
 *
 * @param config 要使用的三人日麻規則設定。
 * @return 以第一位玩家為呼叫者的情境輸入。
 */
internal fun createThreePlayerRiichiScenarioContext(
    config: ThreePlayerRiichiRuleConfig = ThreePlayerRiichiRuleConfig(),
): DebugGameScenarioContext = createScenarioContext(
    module = ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, config),
    playerCount = 3,
    aiOpponentCount = 2,
)

/**
 * 以 [module] 正式開局，第一位玩家是呼叫者，接著 [aiOpponentCount] 位是 AI。
 *
 * @param module 要用於正式初始化的規則模組。
 * @param playerCount 桌上的玩家數量。
 * @param aiOpponentCount 呼叫者以外由 AI 操控的玩家數量。
 * @return 包含初始化遊戲與呼叫者識別碼的情境輸入。
 */
private fun createScenarioContext(
    module: MahjongRuleModule<*>,
    playerCount: Int,
    aiOpponentCount: Int,
): DebugGameScenarioContext {
    val playerIds = List(playerCount) { Uuid.random() }
    val initialized = GameInitializer.initialize(
        id = Uuid.random(),
        playerIds = playerIds,
        module = module,
    )
    val game = Game(
        tableState = initialized.tableState,
        flowConfig = GameFlowConfig(),
        hostId = playerIds.first(),
        roomPlayerIds = playerIds,
        aiPlayerStrategyKeys = playerIds.drop(1).take(aiOpponentCount).associateWith { "mahjongcraft:random" },
    )
    return DebugGameScenarioContext(game, playerIds.first())
}

/**
 * 以目前玩家為 0，依打牌順序取得座位。
 *
 * @param offset 從目前玩家起算的座位偏移。
 * @return 指定偏移的玩家。
 */
internal fun TableState.seat(offset: Int): MahjongPlayer = players[(currentPlayerIndex + offset) % players.size]

/**
 * 將指定玩家的手牌替換為測試用版本，讓合法動作情境仍由同一份桌況建立。
 *
 * @param playerId 要替換手牌的玩家身分。
 * @param hand 要放入該玩家的測試手牌。
 * @return 只替換指定玩家手牌後的桌況。
 */
private fun TableState.withHandFor(
    playerId: Uuid,
    hand: Hand,
): TableState = copy(
    players = players.map { player ->
        if (player.id == playerId) player.copy(hand = hand) else player
    },
)

/** @return 目前桌況規則模組建立的合法動作驗證器。 */
internal fun TableState.scenarioValidator(): LegalActionValidator = scenarioModuleRegistry.getModule(config).createLegalActionValidator()

/**
 * 依活牌索引取得牌張。
 *
 * @param index 活牌序列中的索引。
 * @return 指定位置的牌。
 */
internal fun TableState.liveWallTile(index: Int): IdentifiedTile = tileWall.getAllTiles()[index]

/**
 * 取得活牌序列前端的牌面。
 *
 * @param count 要取得的牌數。
 * @return 活牌前端至多 count 張牌面。
 */
internal fun TableState.liveWallFront(count: Int): List<Tile> = tileWall.getAllTiles().take(count).map { it.tile }

/**
 * [offset] 座位對 [discarderOffset] 座位打出的一張 [tile] 可做的反應。
 *
 * @param offset 回應玩家的座位偏移。
 * @param discarderOffset 捨牌玩家的座位偏移。
 * @param tile 被打出的牌。
 * @return 該情境下可用的反應動作。
 */
internal fun TableState.reactionsOf(
    offset: Int,
    discarderOffset: Int,
    tile: IdentifiedTile,
): List<GameAction> {
    val player = seat(offset)
    val discarder = seat(discarderOffset)
    val contextState = withHandFor(player.id, player.hand.copy(lastDrawn = null))
    return contextState.scenarioValidator().getLegalActions(
        LegalActionContext.discardReaction(contextState, player.id, discarder.id, tile),
    )
}

/**
 * 判斷指定座位能否對另一座位打出的牌榮和。
 *
 * @param offset 回應玩家的座位偏移。
 * @param discarderOffset 捨牌玩家的座位偏移。
 * @param tile 被打出的牌面。
 * @return 合法反應包含榮和時為 true。
 */
internal fun TableState.canRon(
    offset: Int,
    discarderOffset: Int,
    tile: Tile,
): Boolean = reactionsOf(offset, discarderOffset, IdentifiedTile(Uuid.random(), tile)).any { it is GameAction.Ron }

/**
 * [offset] 座位摸到 [drawnTile] 時，自己回合可宣告的特殊動作。
 *
 * @param offset 玩家座位偏移。
 * @param drawnTile 假設摸到的牌。
 * @return 該摸牌情境下可用的特殊動作。
 */
internal fun TableState.ownTurnActions(
    offset: Int,
    drawnTile: IdentifiedTile,
): List<GameAction> {
    val player = seat(offset)
    val contextState = withHandFor(player.id, player.hand.copy(lastDrawn = drawnTile))
    return contextState.scenarioValidator().getLegalActions(LegalActionContext.ownTurn(contextState, player.id))
}

/**
 * 情境載入當下，呼叫者以手上剛摸到的牌可宣告的特殊動作。
 *
 * @return 當前玩家可用的特殊動作。
 */
internal fun TableState.currentOwnTurnActions(): List<GameAction> = scenarioValidator().getLegalActions(LegalActionContext.ownTurn(this, currentPlayer.id))

/**
 * [offset] 座位摸到活牌第一張後，自己回合可宣告的槓。
 *
 * @param offset 玩家座位偏移。
 * @return 該摸牌情境下可用的槓動作。
 */
internal fun TableState.ownTurnKans(offset: Int): List<GameAction.Kan> {
    val player = seat(offset)
    val contextState = withHandFor(player.id, player.hand.copy(lastDrawn = liveWallTile(0)))
    return contextState.scenarioValidator()
        .getLegalActions(LegalActionContext.ownTurn(contextState, player.id))
        .filterIsInstance<GameAction.Kan>()
}

/**
 * [offset] 座位能否搶下家以活牌第一張宣告的槓。
 *
 * @param offset 回應玩家的座位偏移。
 * @param type 下家宣告的槓種類。
 * @return 該情境下存在榮和反應時為 true。
 */
internal fun TableState.canRobKan(offset: Int, type: GameAction.KanType): Boolean {
    val kanTile = liveWallTile(0)
    val player = seat(offset)
    val declarer = seat(1)
    val contextState = withHandFor(player.id, player.hand.copy(lastDrawn = null))
    return contextState.scenarioValidator().getLegalActions(
        LegalActionContext.declarationReaction(
            contextState,
            player.id,
            declarer.id,
            GameAction.Kan(type, kanTile.id, emptyList()),
            kanTile,
        ),
    ).any { it is GameAction.Ron }
}
