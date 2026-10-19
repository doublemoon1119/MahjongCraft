package com.doublemoon1119.mahjongcraft.example.ai

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.RoundPreparationAiContext
import com.doublemoon1119.mahjongcraft.ai.defaultSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/**
 * 範例用的摸切 AI：優先自摸，其次遵守規則指定的捨牌，最後捨出剛摸入的牌。
 *
 * 這個策略只讀取公開的 AI 決策情境，不重新計算合法動作，也不修改權威桌況。
 * 不保存跨次決策的可變資料，同一實例可在背景並行使用。
 */
class ExampleTsumogiriAiStrategy : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
        AiDecisionPhase.OwnTurn -> decideOwnTurn(context)
        AiDecisionPhase.RespondingToDiscard ->
            GameCommand.RespondToDiscard(context.legalActions.chooseReaction())
        AiDecisionPhase.RespondingToRobbing ->
            GameCommand.RespondToRobbing(context.legalActions.chooseReaction())
    }

    /**
     * 自己回合優先選擇合法自摸，否則依序套用強制捨牌、剛摸入牌及立牌清單。
     *
     * @param context 本次決策可見的桌況、合法動作與捨牌限制。
     * @return 要提交的自己回合命令。
     * @throws IllegalStateException 沒有可用的捨牌牌張。
     */
    private fun decideOwnTurn(context: AiDecisionContext): GameCommand {
        if (GameAction.Tsumo in context.legalActions) return GameCommand.Tsumo
        context.forcedDiscardTileId?.let { return GameCommand.Discard(it) }

        val hand = context.snapshot.players.firstOrNull { it.id == context.selfId }?.hand
            ?: error("AI player is missing from the decision snapshot")
        hand.lastDrawn?.let { return GameCommand.Discard(it.id) }
        val standingTile = hand.standingTiles.firstOrNull()
            ?: error("AI has no tile available for a discard")
        return GameCommand.Discard(standingTile.id)
    }

    /**
     * 從合法反應中以穩定順序選擇過牌，若沒有過牌則選擇清單中的第一個合法反應。
     *
     * @return 要提交的合法反應動作。
     * @throws IllegalStateException 合法反應清單為空。
     */
    private fun List<GameAction>.chooseReaction(): GameAction = firstOrNull { it == GameAction.Pass } ?: firstOrNull()
        ?: error("AI has no legal reaction")

    /**
     * 採用公開情境提供的預設局前準備提交。
     *
     * @param context 本次開局準備的可見情境與輸入規格。
     * @return 公開輸入規格產生的預設提交。
     */
    override suspend fun decideRoundPreparation(context: RoundPreparationAiContext) = context.defaultSubmission()
}
