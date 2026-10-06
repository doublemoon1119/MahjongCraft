package com.doublemoon1119.mahjongcraft.ai.riichi

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.ExtensionCommandCandidate
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiHandler
import com.doublemoon1119.mahjongcraft.ai.toOwnHand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction

/**
 * 日麻立直動作的 AI action handler。
 *
 * 每一張打出後仍然聽牌的牌各產生一個候選，候選說明它會打出哪張牌並宣告立直。
 */
object RiichiDeclarationAiHandler : ExtensionGameActionAiHandler<RiichiGameAction.Riichi> {
    override fun createCandidates(
        action: RiichiGameAction.Riichi,
        context: AiDecisionContext,
        module: MahjongRuleModule<*>,
    ): List<ExtensionCommandCandidate> {
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand.toOwnHand()
        val calculator = module.createShantenCalculator()
        return hand.tiles
            .filter { candidate ->
                val remaining = hand.copy(tiles = hand.tiles.filterNot { it.id == candidate.id })
                calculator.calculate(remaining) is ShantenResult.Tenpai
            }
            .map { tile ->
                ExtensionCommandCandidate(
                    command = GameCommand.Extension(RiichiGameCommand(tile.id)),
                    discardTileId = tile.id,
                    declaration = GameAction.Extension(action),
                )
            }
    }
}
