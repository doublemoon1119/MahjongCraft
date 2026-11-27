package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.RuleDecisionAiContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration

/**
 * 依亂數讓一部分決策拖過等待上限的策略，用來模擬伺服器忙碌時 AI 來不及在時間內決定。
 *
 * - 每次決策以 [random] 決定是否拖延。拖延的決策一直等到決策執行器逾時取消它，流程改用固定命令，與伺服器忙碌時相同。
 * - 取消後拖延立刻結束，不會留下仍在執行的工作，所以每次拖延剛好讓一次決策改用固定命令，同一個亂數種子重現同一場。
 * - 沒有拖延的決策直接交給 [delegate]。
 *
 * @property delegate 實際做決定的策略。
 * @property random 決定哪些決策要拖延的亂數。
 * @property rate 每次決策拖延的機率，0 到 1。
 */
internal class SlowDecisionStrategy(
    private val delegate: MahjongAiStrategy,
    private val random: Random,
    private val rate: Double,
) : MahjongAiStrategy {
    override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
        maybeStall()
        return delegate.decideGameCommand(context)
    }

    override suspend fun decideRuleDecision(context: RuleDecisionAiContext): RuleDecisionSubmission {
        maybeStall()
        return delegate.decideRuleDecision(context)
    }

    /** 依 [rate] 決定這次是否拖延。 */
    private suspend fun maybeStall() {
        if (random.nextDouble() < rate) delay(Duration.INFINITE)
    }
}
