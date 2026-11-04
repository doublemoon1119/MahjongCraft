package com.doublemoon1119.mahjongcraft.ai.taiwan

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.ExtensionCommandCandidate
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiHandler
import com.doublemoon1119.mahjongcraft.ai.toOwnHand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanDeclareReadyCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction

/**
 * 台灣麻將宣告聽牌動作的 AI action handler。
 *
 * 每一張規則允許打出、且打出後仍然聽牌的牌各產生一個候選，候選說明它會打出哪張牌並宣告聽牌。
 */
object TaiwanReadyDeclarationAiHandler : ExtensionGameActionAiHandler<TaiwanGameAction.DeclareReady> {
    override fun createCandidates(
        action: TaiwanGameAction.DeclareReady,
        context: AiDecisionContext,
        module: MahjongRuleModule<*>,
    ): List<ExtensionCommandCandidate> {
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand.toOwnHand(module.createTileInterpretationPolicy())
        val calculator = module.createShantenCalculator()
        val discardable = context.discardableTileIds
        return hand.tiles
            .filter { candidate -> discardable == null || candidate.id in discardable }
            .filter { candidate ->
                val remaining = hand.copy(tiles = hand.tiles.filterNot { it.id == candidate.id })
                calculator.calculate(remaining) is ShantenResult.Tenpai
            }
            .map { tile ->
                ExtensionCommandCandidate(
                    command = GameCommand.Extension(TaiwanDeclareReadyCommand(tile.id)),
                    discardTileId = tile.id,
                    declaration = GameAction.Extension(action),
                )
            }
    }
}
