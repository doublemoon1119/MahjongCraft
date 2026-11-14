package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證台灣麻將對局的本局自動操作只在玩家可以行動時觸發。 */
class AutomaticDecisionEvaluatorTaiwanTest {
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
    private val evaluator = AutomaticDecisionEvaluator(
        moduleRegistry = moduleRegistry,
        visibilityPolicy = GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry),
    )

    /** 輪到自己、沒有其他可做的動作時，自動打出剛摸到的牌。 */
    @Test
    fun `auto tsumogiri discards the drawn tile on a plain draw`() {
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val game = game(drawn, pendingRuleDecision = null)

        assertEquals(GameAction.Discard(drawn.id), evaluator.evaluate(game, PLAYER_ID)?.immediateAction?.action)
    }

    /** 規則選擇進行中（例如補花）時，自動操作不搶在規則選擇之前打牌。 */
    @Test
    fun `automatic controls wait while a rule decision is pending`() {
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val pending = PendingRuleDecision.open(
            step = RuleDecisionStep(
                stepId = "test:confirmation",
                stepIndex = 0,
                inputSpecsByPlayerId = mapOf(PLAYER_ID to RuleDecisionInputSpec.Confirmation),
            ),
            timing = RuleDecisionTiming.ROUND_START,
        )

        assertNull(evaluator.evaluate(game(drawn, pending), PLAYER_ID)?.immediateAction)
    }

    /**
     * 建立輪到台麻玩家、剛摸進一張牌且啟用自動打出摸到的牌的對局。
     *
     * @param drawn 剛摸進的牌。
     * @param pendingRuleDecision 進行中的規則選擇；沒有時為 null。
     * @return 測試用對局。
     */
    private fun game(
        drawn: IdentifiedTile,
        pendingRuleDecision: PendingRuleDecision?,
    ): Game {
        val player = FakeMahjongPlayerFactory.create(id = PLAYER_ID, initialSeat = Wind.EAST, hand = Hand(lastDrawn = drawn))
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val state = FakeTableStateFactory.create(players = listOf(player, other), config = TaiwanRuleConfig(), currentPlayerIndex = 0)
        return Game(
            tableState = state,
            flowConfig = GameFlowConfig(),
            enabledAutomaticControlIdsByPlayerId = mapOf(PLAYER_ID to setOf(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI)),
            pendingRuleDecision = pendingRuleDecision,
        )
    }

    private companion object {
        /** 測試中輪到行動的玩家。 */
        val PLAYER_ID: Uuid = Uuid.random()
    }
}
