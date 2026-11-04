package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionWin
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [RuleDecisionWindows] 套用規則選擇結果時的和牌處理。 */
class RuleDecisionWindowsTest {
    private val winner = FakeMahjongPlayerFactory.create()
    private val payer = FakeMahjongPlayerFactory.create()
    private val module = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }.getModule(RiichiRuleConfig())
    private val game = Game(
        tableState = FakeTableStateFactory.create(players = listOf(winner, payer), config = RiichiRuleConfig()),
        flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30.seconds)),
    )
    private val decision = PendingRuleDecision.open(
        step = RuleDecisionStep(stepId = "test:special_win", stepIndex = 0, inputSpecsByPlayerId = emptyMap()),
        timing = RuleDecisionTiming.DRAW_FOLLOW_UP,
    )
    private val outcome = RuleDecisionWinOutcome(
        outcomeId = "test:special_win",
        winnerPlayerIds = setOf(winner.id),
        responsiblePlayerIds = setOf(payer.id),
        winningTileId = Uuid.random(),
    )

    /** 直接成立的和牌與分數變動一起寫入對局，並關閉選擇流程。 */
    @Test
    fun `a closing win is recorded on the game`() {
        val advanced = RuleDecisionWindows.advance(
            game = game.copy(pendingRuleDecision = decision),
            decision = decision,
            resolution = winningResolution(nextStep = null),
            module = module,
            resolverRegistry = RuleDecisionResolverRegistry(),
        )

        assertEquals(outcome, advanced.ruleDecisionWin)
        assertNull(advanced.pendingRuleDecision)
        assertEquals(winner.score + SCORE, advanced.tableState.players.first { it.id == winner.id }.score)
    }

    /** 和牌成立後仍要求下一個選擇步驟時，結束本局的流程無法接手，應以契約錯誤拒絕。 */
    @Test
    fun `a win that keeps the decision open is rejected`() {
        val nextStep = RuleDecisionStep(stepId = "test:next", stepIndex = 1, inputSpecsByPlayerId = mapOf(winner.id to RuleDecisionInputSpec.Confirmation))

        assertFailsWith<RuleContractViolationException> {
            RuleDecisionWindows.advance(
                game = game.copy(pendingRuleDecision = decision),
                decision = decision,
                resolution = winningResolution(nextStep = nextStep),
                module = module,
                resolverRegistry = RuleDecisionResolverRegistry(),
            )
        }
    }

    /** 回報的分數增減與實際寫入桌況的不一致時，應以契約錯誤拒絕。 */
    @Test
    fun `a win whose score deltas disagree with the table is rejected`() {
        val resolution = winningResolution(nextStep = null).let { resolution ->
            resolution.copy(win = resolution.win?.copy(scoreDeltas = mapOf(winner.id to 0, payer.id to 0)))
        }

        assertFailsWith<RuleContractViolationException> {
            RuleDecisionWindows.advance(
                game = game.copy(pendingRuleDecision = decision),
                decision = decision,
                resolution = resolution,
                module = module,
                resolverRegistry = RuleDecisionResolverRegistry(),
            )
        }
    }

    /** 贏家沒有記錄和牌動作時，結束本局的流程找不到贏家，應以契約錯誤拒絕。 */
    @Test
    fun `a win without the winner's win action is rejected`() {
        val resolution = winningResolution(nextStep = null).let { resolution ->
            resolution.copy(
                tableState = resolution.tableState.copy(
                    players = resolution.tableState.players.map { player ->
                        if (player.id == winner.id) player.copy(actionHistory = emptyList()) else player
                    },
                ),
            )
        }

        assertFailsWith<RuleContractViolationException> {
            RuleDecisionWindows.advance(
                game = game.copy(pendingRuleDecision = decision),
                decision = decision,
                resolution = resolution,
                module = module,
                resolverRegistry = RuleDecisionResolverRegistry(),
            )
        }
    }

    /**
     * 建立贏家收 [SCORE]、責任玩家付 [SCORE] 並記錄贏家自摸的規則結果。
     *
     * @param nextStep 結果要求的下一個選擇步驟。
     * @return 直接成立和牌的規則結果。
     */
    private fun winningResolution(nextStep: RuleDecisionStep?) = RuleDecisionResolution(
        tableState = game.tableState.copy(
            players = game.tableState.players.map { player ->
                when (player.id) {
                    winner.id -> player.copy(score = player.score + SCORE).recordAction(GameAction.Tsumo)
                    payer.id -> player.copy(score = player.score - SCORE)
                    else -> player
                }
            },
        ),
        nextStep = nextStep,
        win = RuleDecisionWin(
            outcome = outcome,
            scoreDeltas = mapOf(winner.id to SCORE, payer.id to -SCORE),
            extensionDetail = null,
        ),
    )

    /** 測試常數。 */
    private companion object {
        /** 和牌的付款點數。 */
        const val SCORE = 8
    }
}
