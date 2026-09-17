package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 開發環境限定的腳本 AI，讓 debug 情境的對手依排定的牌山順序行動。
 *
 * 他家捨牌或宣告槓時一律跳過；自己回合打出剛摸到的牌，沒有摸牌時打出第一張立牌；不和牌、不鳴牌、不立直。
 * [declaresKanFirst] 為 `true` 時，自己回合有可宣告的槓就先宣告。
 *
 * @property declaresKanFirst 自己回合是否優先宣告可用的暗槓或加槓。
 */
class DebugScriptedAiStrategy(private val declaresKanFirst: Boolean) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
        AiDecisionPhase.RespondingToDiscard -> GameCommand.RespondToDiscard(GameAction.Pass)
        AiDecisionPhase.RespondingToKan -> GameCommand.RespondToKan(GameAction.Pass)
        AiDecisionPhase.OwnTurn -> decideOwnTurn(context)
    }

    /** 自己回合：必要時先宣告槓，否則打出規則限定的牌、剛摸到的牌或第一張立牌。 */
    private fun decideOwnTurn(context: AiDecisionContext): GameCommand {
        if (declaresKanFirst) {
            context.legalActions.filterIsInstance<GameAction.Kan>().firstOrNull()?.let { kan ->
                return GameCommand.Kan(kan.type, kan.tileId)
            }
        }
        context.forcedDiscardTileId?.let { return GameCommand.Discard(it) }
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand
        val tileId = hand.lastDrawn?.id ?: hand.standingTiles.first().id
        return GameCommand.Discard(tileId)
    }

    /** 腳本 AI 在 [MahjongAiStrategyRegistry] 登記的 key。 */
    companion object {
        /** 只摸切、一律跳過的腳本 AI。 */
        val TSUMOGIRI_KEY: String = MahjongCraftMetadata.id("debug_tsumogiri")

        /** 能槓就先槓，其餘同 [TSUMOGIRI_KEY] 的腳本 AI。 */
        val KAN_FIRST_KEY: String = MahjongCraftMetadata.id("debug_kan_first")
    }
}

/** 登記開發環境限定的腳本 AI；正式環境不呼叫。 */
fun MahjongAiStrategyRegistry.registerDebugScriptedAiStrategies() {
    register(DebugScriptedAiStrategy.TSUMOGIRI_KEY) { DebugScriptedAiStrategy(declaresKanFirst = false) }
    register(DebugScriptedAiStrategy.KAN_FIRST_KEY) { DebugScriptedAiStrategy(declaresKanFirst = true) }
}
