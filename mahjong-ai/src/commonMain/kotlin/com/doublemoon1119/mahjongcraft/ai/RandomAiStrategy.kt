package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.random.Random

/**
 * 純粹隨機選擇的 [MahjongAiStrategy]：不評估手牌好壞、不追求胡牌效率。
 *
 * @property random 用於所有隨機選擇的亂數來源，測試時可注入固定種子的實例讓行為可預期。
 * @property extensionActionRegistry 將規則 extension 動作轉成可執行命令的註冊表。
 */
class RandomAiStrategy(
    private val extensionActionRegistry: ExtensionGameActionAiRegistry,
    private val random: Random = Random.Default,
) : MahjongAiStrategy {
    companion object {
        /** 這個策略在 [MahjongAiStrategyRegistry] 裡登記的 key。 */
        const val KEY = "mahjongcraft:random"
    }

    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
        AiDecisionPhase.RespondingToDiscard ->
            GameCommand.RespondToDiscard(context.legalActions.randomOrNull(random) ?: GameAction.Pass)
        AiDecisionPhase.RespondingToKan ->
            GameCommand.RespondToKan(context.legalActions.randomOrNull(random) ?: GameAction.Pass)
        AiDecisionPhase.OwnTurn -> decideOwnTurn(context)
    }

    /**
     * 自己回合的決策：`legalActions` 裡若有 Tsumo/Kan/九種九牌這類會結束/改變回合的選項
     * （排除 [GameAction.Pass]；無法由 registry 轉成命令的未知擴充動作也不列入），依機率決定要不要
     * 選一個；沒選、或清單裡沒有這類選項時，預設行為是
     * 捨牌——這是 `LegalActionValidator` 既有慣例裡永遠可用的預設動作。
     */
    private fun decideOwnTurn(context: AiDecisionContext): GameCommand {
        val actionableOptions = context.legalActions.flatMap { it.toOwnTurnCommands(context) }
        if (actionableOptions.isEmpty() || !random.nextBoolean()) {
            return discardRandomTile(context)
        }

        return actionableOptions.random(random)
    }

    /** 將自己回合的合法動作轉成實際可執行命令。 */
    private fun GameAction.toOwnTurnCommands(context: AiDecisionContext): List<GameCommand> = when (this) {
        GameAction.Tsumo -> listOf(GameCommand.Tsumo)
        is GameAction.Kan -> listOf(GameCommand.Kan(type, tileId))
        is GameAction.ExhaustiveDraw -> listOf(GameCommand.DeclareExhaustiveDraw(reason))
        is GameAction.Extension -> extensionActionRegistry.createCandidates(value, context).map { it.command }
        else -> emptyList()
    }

    /**
     * 從 [AiDecisionContext.snapshot] 裡這位 AI 自己的手牌（立牌 + 剛摸到的牌）隨機挑一張捨棄。
     */
    private fun discardRandomTile(context: AiDecisionContext): GameCommand {
        context.forcedDiscardTileId?.let { return GameCommand.Discard(it) }
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand
        return GameCommand.Discard(hand.standingTiles.map { it.id }.random(random))
    }
}
