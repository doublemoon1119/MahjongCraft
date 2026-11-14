package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializationResult
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.RoundAdvancementResult
import kotlin.uuid.Uuid

/**
 * 以正式開局流程為這一桌重新開始第一局，保留目前的座位順序；莊家預設沿用目前的莊家。
 *
 * 與開新對局不同，不重新洗座位，因此玩家不必換座位；擲骰開門、建牌牆與發牌照常進行。分數設為 [module] 的初始分數，
 * 規則組件重新初始化，原本規則留下的玩家狀態全部清除。亂數取自新產生的種子，不消耗對局本身的局序，
 * 因此之後正式換局使用的亂數不受影響。
 *
 * @param context 目前對局與指令發起者。
 * @param module 情境載入後使用的規則模組。
 * @param dealerPlayerId 第一局的莊家。
 * @return 正式初始化的結果。
 */
internal fun formalFirstRoundKeepingSeats(
    context: DebugGameScenarioContext,
    module: MahjongRuleModule<*>,
    dealerPlayerId: Uuid = context.currentGame.tableState.dealerPlayerId,
): GameInitializationResult {
    val current = context.currentGame.tableState
    val players = current.players.map { player -> player.copy(components = ComponentSet.EMPTY) }
    val position = module.createMatchProgressionPolicy().initialPosition(players.size)
    val randomness = MatchSeed.generate().roundRandomness(roundOrdinal = 0)
    val initialized = GameInitializer.startNextRound(
        gameId = current.id,
        roundAdvancement = RoundAdvancementResult(
            players = players,
            dealerPlayerId = dealerPlayerId,
            roundSerial = current.roundSerial + 1,
            roundNumber = position.roundNumber,
            comboCount = 0,
            prevalentWind = position.prevalentWind,
            roundPosition = position,
        ),
        module = module,
        randomness = randomness,
    )
    return initialized.copy(tableState = initialized.tableState.init()).initializeRuleComponents(
        ruleModuleId = module.id,
        registry = context.componentRegistry,
        random = randomness.components,
    )
}
