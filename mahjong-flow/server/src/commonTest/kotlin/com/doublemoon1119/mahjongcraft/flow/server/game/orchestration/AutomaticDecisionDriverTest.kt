package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [AutomaticDecisionDriver] 的權威反應流程測試。 */
class AutomaticDecisionDriverTest {
    /** 啟用不吃碰槓且只剩鳴牌或放過時，立即經共用 mapper 提交放過。 */
    @Test
    fun `test decline calls maps automatic pass through reaction context`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val contextResolver = PlayerActionContextResolver()
        val evaluator = AutomaticDecisionEvaluator(moduleRegistry, contextResolver)
        val driver = AutomaticDecisionDriver(
            repository,
            evaluator,
            GameActionCommandMapper(ExtensionGameActionCommandFactoryRegistry()),
            contextResolver,
        )
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(discarded),
        )
        val matchingTiles = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }
        val respondent = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = matchingTiles),
            playerRuleState = RiichiPlayerState(),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarder.id, discarded.id, setOf(respondent.id)),
        )
        repository.setGame(
            Game(
                tableState = state,
                flowConfig = GameFlowConfig(),
                enabledAutomaticControlIdsByPlayerId = mapOf(
                    respondent.id to setOf(BuiltInAutomaticControlIds.DECLINE_CALLS),
                ),
            ),
        )

        val result = driver.resolveNextAction(state.id)

        assertEquals(respondent.id to GameCommand.RespondToDiscard(GameAction.Pass), result)
    }
}
