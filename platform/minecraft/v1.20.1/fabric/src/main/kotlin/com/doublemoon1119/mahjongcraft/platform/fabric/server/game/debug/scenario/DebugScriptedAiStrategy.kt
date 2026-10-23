package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiPullNorthCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 開發環境限定的腳本 AI，讓 debug 情境的對手依排定的牌山順序行動。
 *
 * 他家捨牌或宣告槓、拔北時一律跳過；自己回合優先遵守規則限定的捨牌，否則摸切或打出第一張立牌。
 * 預設不和牌、不吃碰、不立直。
 * [declaresKanFirst] 為 `true` 時，自己回合有可宣告的槓就先宣告；[pullsNorthFirst] 為 `true` 時，三人日麻自己回合
 * 可以拔北就先拔北；[winsFirst] 為 `true` 時，可以榮和或自摸就和牌。只依決策情境計算，沒有可變狀態。
 *
 * @property declaresKanFirst 自己回合是否優先宣告可用的暗槓或加槓。
 * @property pullsNorthFirst 自己回合是否優先拔北。
 * @property winsFirst 是否在可以和牌時和牌。
 */
class DebugScriptedAiStrategy(
    private val declaresKanFirst: Boolean,
    private val pullsNorthFirst: Boolean = false,
    private val winsFirst: Boolean = false,
) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
        AiDecisionPhase.RespondingToDiscard ->
            context.discardReactionCommands.firstOrNull { winsFirst && it.action is GameAction.Ron }
                ?: GameCommand.RespondToDiscard(GameAction.Pass, checkNotNull(context.discardReactionId))
        AiDecisionPhase.RespondingToRobbing -> GameCommand.RespondToRobbing(GameAction.Pass)
        AiDecisionPhase.OwnTurn -> decideOwnTurn(context)
    }

    /**
     * 自己回合優先自摸、拔北或宣告槓，否則打出規則限定的牌、剛摸到的牌或第一張立牌。
     *
     * @param context 含可用動作與自身手牌的決策情境。
     * @return 對應的拔北、槓或捨牌命令。
     */
    private fun decideOwnTurn(context: AiDecisionContext): GameCommand {
        if (winsFirst && GameAction.Tsumo in context.legalActions) return GameCommand.Tsumo
        if (pullsNorthFirst && PULL_NORTH_GAME_ACTION in context.legalActions) {
            return GameCommand.Extension(RiichiPullNorthCommand)
        }
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

        /** 能拔北就先拔北，其餘同 [TSUMOGIRI_KEY] 的腳本 AI。 */
        val PULL_NORTH_FIRST_KEY: String = MahjongCraftMetadata.id("debug_pull_north_first")

        /** 能和牌就和牌，其餘同 [TSUMOGIRI_KEY] 的腳本 AI。 */
        val WIN_FIRST_KEY: String = MahjongCraftMetadata.id("debug_win_first")
    }
}
