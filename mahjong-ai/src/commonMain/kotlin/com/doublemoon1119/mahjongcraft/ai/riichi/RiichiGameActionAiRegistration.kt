package com.doublemoon1119.mahjongcraft.ai.riichi

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction

/** 登記內建日麻立直動作的 AI action handler。 */
fun ExtensionGameActionAiRegistry.registerRiichiGameActionHandler(moduleRegistry: MahjongModuleRegistry) {
    register(RiichiGameAction.Riichi::class) { _, context ->
        val player = context.snapshot.players.first { it.id == context.selfId }
        val calculator = moduleRegistry.getModule(context.snapshot.config).createShantenCalculator()
        val visibleTiles = (player.hand.standingTiles + listOfNotNull(player.hand.lastDrawn)).mapNotNull { snapshot ->
            snapshot.tile?.let { IdentifiedTile(snapshot.id, it) }
        }
        val candidates = visibleTiles.filter { candidate ->
            val remainingTiles = visibleTiles.filterNot { it.id == candidate.id }
            calculator.calculate(Hand(tiles = remainingTiles)) is ShantenResult.Tenpai
        }
        candidates.map { GameCommand.Extension(RiichiGameCommand(it.id)) }
    }
}
