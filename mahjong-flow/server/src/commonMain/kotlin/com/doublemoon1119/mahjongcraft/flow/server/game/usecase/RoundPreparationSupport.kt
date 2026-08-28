package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolver
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/** 連續套用不需要玩家輸入的開局準備步驟，直到等待玩家或流程完成。 */
internal fun resolveAutomaticSteps(
    initialGame: Game,
    resolver: RoundPreparationResolver,
    module: MahjongRuleModule<*>,
): Game {
    var game = initialGame
    repeat(MAX_AUTOMATIC_PREPARATION_STEPS) {
        val preparation = game.pendingRoundPreparation ?: return game
        if (preparation.participantPlayerIds.isNotEmpty()) return game
        val resolution = resolver.resolve(game.tableState, preparation, module)
        game = game.copy(tableState = resolution.tableState, pendingRoundPreparation = resolution.nextStep)
    }
    error("Round preparation did not converge after $MAX_AUTOMATIC_PREPARATION_STEPS automatic steps")
}

/** 單次自動推進允許的最大準備步驟數。 */
private const val MAX_AUTOMATIC_PREPARATION_STEPS: Int = 128
