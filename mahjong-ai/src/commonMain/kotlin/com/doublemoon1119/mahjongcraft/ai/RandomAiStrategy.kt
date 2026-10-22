package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 純粹隨機選擇的 [MahjongAiStrategy]：不評估手牌好壞、不追求胡牌效率。
 *
 * 除了 [random] 之外沒有可變狀態；預設的 [Random.Default] 可跨執行緒使用，注入其他亂數來源時也必須可跨執行緒使用。
 *
 * @property random 用於所有隨機選擇的亂數來源，測試時可注入固定種子的實例讓行為可預期。
 * @property extensionActionRegistry 將規則 extension 動作轉成可執行命令的註冊表。
 */
class RandomAiStrategy(
    private val extensionActionRegistry: ExtensionGameActionAiRegistry,
    private val random: Random = Random.Default,
) : MahjongAiStrategy {
    /** 隨機策略的登記識別碼。 */
    companion object {
        /** 這個策略在 [MahjongAiStrategyRegistry] 裡登記的 key。 */
        const val KEY = "mahjongcraft:random"
    }

    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
        AiDecisionPhase.RespondingToDiscard ->
            context.discardReactionCommands.randomOrNull(random)
                ?: context.legalActions.mapNotNull { action ->
                    action.toBuiltinDiscardReactionCommand(checkNotNull(context.discardReactionId))
                }.randomOrNull(random)
                ?: GameCommand.RespondToDiscard(GameAction.Pass, checkNotNull(context.discardReactionId))
        AiDecisionPhase.RespondingToRobbing ->
            GameCommand.RespondToRobbing(context.legalActions.randomOrNull(random) ?: GameAction.Pass)
        AiDecisionPhase.OwnTurn -> decideOwnTurn(context)
    }

    /**
     * 自己回合的決策：`legalActions` 裡若有 Tsumo/Kan/九種九牌這類會結束/改變回合的選項
     * （排除 [GameAction.Pass]；無法由 registry 轉成命令的未知擴充動作也不列入），依機率決定要不要
     * 選一個；沒選、或清單裡沒有這類選項時，預設行為是
     * 捨牌——這是 `LegalActionValidator` 既有慣例裡永遠可用的預設動作。
     *
     * @param context 本次決策可見的手牌、合法動作與捨牌限制。
     * @return 選中的動作命令，或隨機捨牌命令。
     */
    private fun decideOwnTurn(context: AiDecisionContext): GameCommand {
        val actionableOptions = context.legalActions.flatMap { it.toOwnTurnCommands(context) }
        if (actionableOptions.isEmpty() || !random.nextBoolean()) {
            return discardRandomTile(context)
        }

        return actionableOptions.random(random)
    }

    /**
     * 將自己回合的合法動作轉成實際可執行命令。
     *
     * @param context 提供擴充動作建立命令時所需的決策資訊。
     * @return 此動作對應的命令；無法轉換的動作回傳空清單。
     */
    private fun GameAction.toOwnTurnCommands(context: AiDecisionContext): List<GameCommand> = when (this) {
        GameAction.Tsumo -> listOf(GameCommand.Tsumo)
        is GameAction.Kan -> listOf(GameCommand.Kan(type, tileId))
        is GameAction.ExhaustiveDraw -> listOf(GameCommand.DeclareExhaustiveDraw(reason))
        is GameAction.Extension -> extensionActionRegistry.createCandidates(value, context).map { it.command }
        else -> emptyList()
    }

    /**
     * 將內建捨牌反應動作轉成完整命令；擴充動作必須由呼叫端提供具體候選。
     *
     * @param reactionId 本次反應視窗的識別碼。
     * @return 內建反應命令；非內建反應時為 null。
     */
    private fun GameAction.toBuiltinDiscardReactionCommand(reactionId: Uuid): GameCommand.RespondToDiscard? = when (this) {
        is GameAction.Chi -> GameCommand.RespondToDiscard(this, reactionId, withTiles)
        is GameAction.Pon -> GameCommand.RespondToDiscard(this, reactionId, withTiles)
        is GameAction.Kan -> takeIf { type == GameAction.KanType.OPEN_KAN }?.let { GameCommand.RespondToDiscard(it, reactionId, withTiles) }
        GameAction.Pass, is GameAction.Ron -> GameCommand.RespondToDiscard(this, reactionId)
        else -> null
    }

    /**
     * 優先捨棄規則指定的牌，未指定時從自己的立牌清單隨機挑一張；清單已包含剛摸到的牌。
     *
     * @param context 本次決策的手牌快照與指定捨牌限制。
     * @return 捨棄所選牌張的命令。
     */
    private fun discardRandomTile(context: AiDecisionContext): GameCommand {
        context.forcedDiscardTileId?.let { return GameCommand.Discard(it) }
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand
        return GameCommand.Discard(hand.standingTiles.map { it.id }.random(random))
    }
}
