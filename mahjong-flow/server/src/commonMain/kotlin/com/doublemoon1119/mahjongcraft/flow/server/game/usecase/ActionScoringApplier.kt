package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringContext
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 詢問規則的動作即時計分 policy，驗證後把收付寫入候選桌況。 */
internal object ActionScoringApplier {
    /**
     * 套用結果。
     *
     * @property tableState 已寫入收付的候選桌況；不收付時為原本的候選桌況。
     * @property scoring 已套用的收付；不收付時為 null。
     */
    data class Applied(
        val tableState: TableState,
        val scoring: AppliedActionScoring?,
    )

    /**
     * 計算並套用一次已確定成立的動作的收付。
     *
     * @param before 動作套用前的權威桌況。
     * @param after 已套用動作本身、尚未補牌的候選桌況。
     * @param actorPlayerId 執行動作的玩家。
     * @param action 已確定成立的動作。
     * @param sourcePlayerId 鳴取他家捨牌時被鳴牌的玩家；其他動作為 null。
     * @param module 對局採用的規則模組。
     * @return 套用後的候選桌況與收付。
     * @throws RuleContractViolationException 規則回傳的收付不合法時。
     */
    fun apply(
        before: TableState,
        after: TableState,
        actorPlayerId: Uuid,
        action: GameAction,
        sourcePlayerId: Uuid?,
        module: MahjongRuleModule<*>,
    ): Applied {
        val scoring = module.createActionScoringPolicy().resolve(
            ActionScoringContext(
                tableStateBeforeAction = before,
                tableStateAfterAction = after,
                actorPlayerId = actorPlayerId,
                action = action,
                sourcePlayerId = sourcePlayerId,
            ),
        ) ?: return Applied(after, scoring = null)

        fun violation(detail: String): Nothing = throw RuleContractViolationException(
            GameAbortReasonIds.RULE_EXECUTION_FAILED,
            "Action scoring for $action is invalid: $detail",
        )

        val playerIds = after.players.mapTo(mutableSetOf()) { it.id }
        if (scoring.scoreDeltas.isEmpty()) violation("no player score changes")
        if (!playerIds.containsAll(scoring.scoreDeltas.keys)) violation("score changes include a player outside the game")
        if (scoring.scoreDeltas.values.any { it == 0 }) violation("score changes include zero")
        val total = scoring.scoreDeltas.values.sumOf { it.toLong() } + scoring.potDeposit.toLong()
        if (total != 0L) violation("player changes and pot deposit do not balance")
        val players = after.players.map { player ->
            val delta = scoring.scoreDeltas[player.id] ?: return@map player
            val score = player.score.toLong() + delta.toLong()
            if (score !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) violation("score of ${player.id} overflows")
            player.copy(score = score.toInt())
        }
        return Applied(
            tableState = after.copy(players = players, dynamicRuleState = scoring.dynamicRuleState),
            scoring = AppliedActionScoring(
                actorPlayerId = actorPlayerId,
                action = action,
                scoreDeltas = scoring.scoreDeltas,
                potDeposit = scoring.potDeposit,
            ),
        )
    }
}
