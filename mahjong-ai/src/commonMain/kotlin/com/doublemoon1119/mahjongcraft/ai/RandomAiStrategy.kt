package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.random.Random

/**
 * [MahjongAiStrategy] 的 Dummy 實作：純粹隨機選擇，不評估手牌好壞、不追求胡牌效率。
 *
 * 用途是讓 AI 玩家能先動起來、把整條「AI 該不該行動、行動結果怎麼套用」的流程跑通，之後再用真正
 * 會評估局面的策略取代或並存（[MahjongAiStrategy] 介面本身已經支援多個實作）。
 *
 * 立直（[GameAction.Riichi]）刻意不實作：立直宣告除了選擇要不要立直，還要決定打哪張牌才能維持
 * 聽牌，隨機打很容易導致詐胡而失敗；要正確判斷需要向聽數計算，超出「Dummy 隨機」的範圍——即使
 * [AiDecisionContext.legalActions] 裡出現 `GameAction.Riichi`，這個策略也永遠不會選它，一律
 * 走預設捨牌。
 *
 * @property random 用於所有隨機選擇的亂數來源，測試時可注入固定種子的實例讓行為可預期。
 */
class RandomAiStrategy(private val random: Random = Random.Default) : MahjongAiStrategy {
    override suspend fun decide(context: AiDecisionContext): GameCommand = when (context.phase) {
        AiDecisionPhase.RespondingToDiscard ->
            GameCommand.RespondToDiscard(context.legalActions.randomOrNull(random) ?: GameAction.Pass)
        AiDecisionPhase.RespondingToChankan ->
            GameCommand.RespondToChankan(context.legalActions.randomOrNull(random) ?: GameAction.Pass)
        AiDecisionPhase.OwnTurn -> decideOwnTurn(context)
    }

    /**
     * 自己回合的決策：`legalActions` 裡若有 Tsumo/Kan/九種九牌這類會結束/改變回合的選項
     * （排除 [GameAction.Riichi] 與 [GameAction.Pass]，前者這個策略永遠不選，後者不是一個
     * 可以直接執行的動作），依機率決定要不要選一個；沒選、或清單裡沒有這類選項時，預設行為是
     * 捨牌——這是 `LegalActionValidator` 既有慣例裡永遠可用的預設動作。
     */
    private fun decideOwnTurn(context: AiDecisionContext): GameCommand {
        val actionableOptions = context.legalActions.filter { it !is GameAction.Riichi && it != GameAction.Pass }
        if (actionableOptions.isEmpty() || !random.nextBoolean()) {
            return discardRandomTile(context)
        }

        return when (val chosen = actionableOptions.random(random)) {
            GameAction.Tsumo -> GameCommand.Tsumo
            is GameAction.Kan -> GameCommand.Kan(chosen.type, chosen.tileId)
            is GameAction.ExhaustiveDraw -> GameCommand.KyuushuKyuuhai
            else -> discardRandomTile(context)
        }
    }

    /**
     * 從 [AiDecisionContext.snapshot] 裡這位 AI 自己的手牌（立牌 + 剛摸到的牌）隨機挑一張捨棄。
     */
    private fun discardRandomTile(context: AiDecisionContext): GameCommand {
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand
        val candidateIds = hand.standingTiles.map { it.id } + listOfNotNull(hand.lastDrawn?.id)
        return GameCommand.Discard(candidateIds.random(random))
    }
}
