package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** 一般建置中執行的少量 AI 對局：確認所有內建策略能以正式流程打完整場，且模擬器能偵測並重播問題。 */
class MatchSimulationSmokeTest {
    /**
     * 四個內建策略同桌打完一場東風戰，沒有不合法命令、流程沒有卡住、點數守恆、正常結束；
     * 每一局的榮和與放銃互相對應，榮和一定是和牌。
     */
    @Test
    fun `every built-in strategy plays a full east match`() = runTest(timeout = SMOKE_TIMEOUT) {
        val lineup = listOf(BuiltInAiStrategyKeys.BEGINNER, BuiltInAiStrategyKeys.INTERMEDIATE, BuiltInAiStrategyKeys.ADVANCED, RandomAiStrategy.KEY)

        val result = MatchSimulator(RiichiRuleConfig(gameLength = RiichiGameLength.East)).play(lineup)

        assertNull(result.failure, result.failure?.describe())
        assertTrue(result.rounds.size >= RiichiGameLength.East.totalRounds, "rounds played: ${result.rounds.size}")
        assertEquals(setOf(1, 2, 3, 4), result.placementsByPlayer.values.toSet())
        assertEquals(lineup.toSet(), result.strategyKeysByPlayer.values.toSet())
        result.rounds.forEach { seats ->
            assertEquals(seats.any { it.wonByRon }, seats.any { it.dealtIn }, "ron and deal-in must match: $seats")
            assertTrue(seats.none { it.wonByRon && !it.won }, "a ron must be a win: $seats")
        }
    }

    /** 送出不合法命令的策略會被偵測，且從那一局的開局桌況重新推進時同樣發生。 */
    @Test
    fun `an illegal command is detected and reproduced on replay`() = runTest(timeout = SMOKE_TIMEOUT) {
        val simulator = MatchSimulator(RiichiRuleConfig()) { registry, _ ->
            registry.register(ILLEGAL_KEY) { DiscardsForeignTileStrategy() }
        }

        val result = simulator.play(List(4) { ILLEGAL_KEY })

        val failure = assertNotNull(result.failure)
        assertIs<IllegalAiCommandException>(failure.cause)
        assertEquals(true, failure.reproducedOnReplay)
        assertTrue(failure.trace.isNotEmpty())
        assertTrue(result.placementsByPlayer.isEmpty())
    }

    /** 自己回合一律打出一張不在手中的牌，其餘情境都過的測試策略。 */
    private class DiscardsForeignTileStrategy : MahjongAiStrategy {
        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.OwnTurn -> GameCommand.Discard(Uuid.random())
            AiDecisionPhase.RespondingToDiscard -> GameCommand.RespondToDiscard(GameAction.Pass)
            AiDecisionPhase.RespondingToKan -> GameCommand.RespondToKan(GameAction.Pass)
        }
    }

    /** 測試常數。 */
    private companion object {
        /** 送出不合法命令的測試策略 key。 */
        const val ILLEGAL_KEY = "test:illegal"

        /** 單場模擬的時間上限。 */
        val SMOKE_TIMEOUT = 10.minutes
    }
}
