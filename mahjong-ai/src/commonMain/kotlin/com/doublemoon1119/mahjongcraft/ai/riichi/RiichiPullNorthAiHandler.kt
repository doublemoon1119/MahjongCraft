package com.doublemoon1119.mahjongcraft.ai.riichi

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.ExtensionCommandCandidate
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiHandler
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiPullNorthCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical

/**
 * 三人日麻拔北動作的 AI action handler。
 *
 * 產生一個移出手中北的候選；要拔哪一張北由規則決定，評估時北與北之間沒有差別。
 */
object RiichiPullNorthAiHandler : ExtensionGameActionAiHandler<RiichiGameAction.PullNorth> {
    override fun createCandidates(
        action: RiichiGameAction.PullNorth,
        context: AiDecisionContext,
        module: MahjongRuleModule<*>,
    ): List<ExtensionCommandCandidate> {
        val hand = context.snapshot.players.first { it.id == context.selfId }.hand
        val north = hand.lastDrawn?.takeIf { it.tile?.riichiCanonical == Tile.Honor.North }
            ?: hand.standingTiles.firstOrNull { it.tile?.riichiCanonical == Tile.Honor.North }
            ?: return emptyList()
        return listOf(
            ExtensionCommandCandidate(
                command = GameCommand.Extension(RiichiPullNorthCommand),
                declaration = GameAction.Extension(action),
                setAsideTileId = north.id,
            ),
        )
    }
}
