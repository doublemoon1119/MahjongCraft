package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證 [decisionPhaseFor] 判斷對局目前是否正在等某位玩家做決定。 */
class PlayerDecisionPhaseResolutionTest {
    /** 無狀態的操作權解析器。 */
    private val resolver = PlayerActionContextResolver()

    /** 輪到摸完牌的玩家打牌時在等他，同桌其他玩家沒有被等待。 */
    @Test
    fun `the current player is awaited on their own turn`() {
        val currentId = Uuid.random()
        val otherId = Uuid.random()
        val game = gameOf(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = currentId, hand = Hand(lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.East))),
                    FakeMahjongPlayerFactory.create(id = otherId),
                ),
            ),
        )

        assertEquals(PlayerDecisionPhase.OWN_TURN, game.decisionPhaseFor(currentId, resolver))
        assertNull(game.decisionPhaseFor(otherId, resolver))
    }

    /** 別人打出的牌還沒回應時在等他。已經回應或不能回應的玩家沒有被等待。 */
    @Test
    fun `only players who still have to react to a discard are awaited`() {
        val answeredId = Uuid.random()
        val waitingId = Uuid.random()
        val bystanderId = Uuid.random()
        val game = gameOf(
            tableState = FakeTableStateFactory.create(
                players = listOf(answeredId, waitingId, bystanderId).map { FakeMahjongPlayerFactory.create(id = it) },
                pendingReaction = PendingReaction(
                    discarderId = Uuid.random(),
                    tileId = Uuid.random(),
                    eligiblePlayerIds = linkedSetOf(answeredId, waitingId),
                    responses = mapOf(answeredId to GameAction.Pass),
                    ronPlayerIds = linkedSetOf(answeredId, waitingId),
                    id = Uuid.random(),
                ),
            ),
        )

        assertEquals(PlayerDecisionPhase.DISCARD_REACTION, game.decisionPhaseFor(waitingId, resolver))
        assertNull(game.decisionPhaseFor(answeredId, resolver))
        assertNull(game.decisionPhaseFor(bystanderId, resolver))
    }

    /** 規則選擇中還沒提交的參與者被等待。提交之後不再被等待。 */
    @Test
    fun `rule decision participants are awaited until they submit`() {
        val waitingId = Uuid.random()
        val submittedId = Uuid.random()
        val step = RuleDecisionStep(
            stepId = "mahjongcraft:test_step",
            stepIndex = 0,
            inputSpecsByPlayerId = mapOf(
                waitingId to RuleDecisionInputSpec.Confirmation,
                submittedId to RuleDecisionInputSpec.Confirmation,
            ),
        )
        val game = gameOf(
            tableState = FakeTableStateFactory.create(
                players = listOf(waitingId, submittedId).map { FakeMahjongPlayerFactory.create(id = it) },
            ),
            pendingRuleDecision = PendingRuleDecision(
                id = Uuid.random(),
                timing = RuleDecisionTiming.entries.first(),
                step = step,
                submissionsByPlayerId = mapOf(submittedId to RuleDecisionSubmission.Confirmed),
            ),
        )

        assertEquals(PlayerDecisionPhase.RULE_DECISION, game.decisionPhaseFor(waitingId, resolver))
        assertNull(game.decisionPhaseFor(submittedId, resolver))
    }

    /**
     * 建立只帶指定桌況與規則選擇的對局。
     *
     * @param tableState 對局的桌況。
     * @param pendingRuleDecision 進行中的規則選擇。沒有時為 null。
     * @return 測試用的對局。
     */
    private fun gameOf(tableState: TableState, pendingRuleDecision: PendingRuleDecision? = null): Game = Game(
        tableState = tableState,
        flowConfig = GameFlowConfig(),
        pendingRuleDecision = pendingRuleDecision,
    )
}
