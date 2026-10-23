package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoring
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringContext
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_STICK_POINTS
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState

/**
 * 拔北與槓牌成立當下的收付。
 *
 * - 拔北：拔北的玩家把一根立直棒的點數付進供託，之後和牌的玩家連同立直棒一起收走。
 * - 明槓：放槓的玩家付 [OPEN_KAN_PAYMENT] 給開槓的玩家。
 * - 暗槓與加槓：其他玩家各付 [CLOSED_KAN_PAYMENT_PER_PLAYER] 給開槓的玩家。
 *
 * 其他動作不收付。
 */
internal object DebugAutoNorthActionScoringPolicy : ActionScoringPolicy {
    override fun resolve(context: ActionScoringContext): ActionScoring? {
        val stateAfterAction = context.tableStateAfterAction
        return when {
            context.action == PULL_NORTH_GAME_ACTION -> {
                val dynamicState = stateAfterAction.dynamicRuleState as? RiichiDynamicState ?: return null
                ActionScoring(
                    scoreDeltas = mapOf(context.actorPlayerId to -RIICHI_STICK_POINTS),
                    potDeposit = RIICHI_STICK_POINTS,
                    dynamicRuleState = dynamicState.copy(riichiStickCount = dynamicState.riichiStickCount + 1),
                )
            }
            context.action is GameAction.Kan -> {
                val payerIds = context.sourcePlayerId?.let(::listOf)
                    ?: stateAfterAction.players.map { it.id }.filter { it != context.actorPlayerId }
                val payment = if (context.sourcePlayerId != null) OPEN_KAN_PAYMENT else CLOSED_KAN_PAYMENT_PER_PLAYER
                ActionScoring(
                    scoreDeltas = payerIds.associateWith { -payment } + (context.actorPlayerId to payment * payerIds.size),
                    potDeposit = 0,
                    dynamicRuleState = stateAfterAction.dynamicRuleState,
                )
            }
            else -> null
        }
    }

    /** 明槓時放槓者付給開槓者的點數。 */
    const val OPEN_KAN_PAYMENT: Int = 1000

    /** 暗槓與加槓時其他每位玩家付給開槓者的點數。 */
    const val CLOSED_KAN_PAYMENT_PER_PLAYER: Int = 500
}
