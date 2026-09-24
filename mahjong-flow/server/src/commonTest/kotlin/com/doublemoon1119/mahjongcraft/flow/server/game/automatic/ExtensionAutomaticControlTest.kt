package com.doublemoon1119.mahjongcraft.flow.server.game.automatic

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomaticDecisionDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionCommandMapper
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsResult
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlAction
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlPolicy
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/**
 * 內建規則以外的自動操作控制走完整條權威路徑。
 *
 * 這裡的規則模組只是測試替身：它包住正式日麻模組，額外宣告一個自己的控制 ID 與對應政策，用來驗證
 * 「控制 ID 由規則宣告、Flow 只負責轉送」這件事對非內建 ID 同樣成立，正式規則模組不受影響。
 */
class ExtensionAutomaticControlTest {
    /** 權威更新用例接受規則自行宣告的控制 ID。 */
    @Test
    fun `an extension control id is accepted by the authoritative update`() = runTest {
        val repository = FakeGameRepository()
        val useCase = UpdatePlayerAutomaticControlsUseCase(repository, extensionModuleRegistry())
        val (game, playerId) = createGame()
        repository.setGame(game)

        val outcome = useCase(
            game.id,
            playerId,
            expectedRevision = 0L,
            enabledControlIds = setOf(EXTENSION_CONTROL_ID),
        )

        val accepted = assertIs<UpdatePlayerAutomaticControlsResult.Accepted>(outcome)
        assertEquals(setOf(EXTENSION_CONTROL_ID), accepted.snapshot.enabledControlIds)
        assertEquals(setOf(EXTENSION_CONTROL_ID), repository.getGame(game.id)?.enabledAutomaticControlIdsByPlayerId?.get(playerId))
    }

    /** 規則政策針對自訂控制產生的動作，經由正式 mapper 成為可執行命令。 */
    @Test
    fun `an extension control drives an immediate command through the shared mapper`() = runTest {
        val repository = FakeGameRepository()
        val (game, playerId) = createGame(enabledControlIds = setOf(EXTENSION_CONTROL_ID))
        repository.setGame(game)
        val contextResolver = PlayerActionContextResolver()
        val driver = AutomaticDecisionDriver(
            repository,
            AutomaticDecisionEvaluator(extensionModuleRegistry(), contextResolver),
            GameActionCommandMapper(ExtensionGameActionCommandFactoryRegistry()),
            contextResolver,
        )

        assertEquals(
            playerId to GameCommand.RespondToDiscard(GameAction.Pass),
            driver.resolveNextAction(game.id),
        )
    }

    /** 建立一場有鳴牌反應視窗的對局，反應者即為受測玩家。 */
    private fun createGame(enabledControlIds: Set<String> = emptySet()): Pair<Game, Uuid> {
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(discardPile = FakeDiscardPile().discardTile(discarded))
        val respondent = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }),
            playerRuleState = RiichiPlayerState(),
        )
        val state: TableState = FakeTableStateFactory.create(
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarder.id, discarded.id, setOf(respondent.id)),
        )
        val game = Game(
            tableState = state,
            flowConfig = GameFlowConfig(),
            enabledAutomaticControlIdsByPlayerId = if (enabledControlIds.isEmpty()) {
                emptyMap()
            } else {
                mapOf(respondent.id to enabledControlIds)
            },
        )
        return game to respondent.id
    }

    /** 只登記測試替身的 registry；替身的規則行為完全沿用正式日麻模組，只多一個自訂控制。 */
    private fun extensionModuleRegistry() = MahjongModuleRegistryImpl().apply {
        register(RiichiRuleConfig::class, EXTENSION_RULE_MODULE_ID) { config, _ ->
            ExtensionControlRuleModule(RiichiRuleModule("mahjongcraft:riichi", config))
        }
        freeze()
    }

    /** 額外宣告一個自訂控制與對應政策的規則模組測試替身。 */
    private class ExtensionControlRuleModule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun getSupportedAutomaticControlIds(): Set<String> = delegate.getSupportedAutomaticControlIds() + EXTENSION_CONTROL_ID

        override fun createAutomaticControlPolicy(): AutomaticControlPolicy = AutomaticControlPolicy { context ->
            if (EXTENSION_CONTROL_ID in context.enabledControlIds && GameAction.Pass in context.legalActions) {
                AutomaticControlResult(immediateAction = AutomaticControlAction(GameAction.Pass))
            } else {
                AutomaticControlResult()
            }
        }
    }

    private companion object {
        /** 測試替身規則的 ID，不屬於任何內建規則。 */
        const val EXTENSION_RULE_MODULE_ID = "example:extension_rule"

        /** 測試替身規則自行宣告的控制 ID，不屬於任何內建控制。 */
        const val EXTENSION_CONTROL_ID = "example:auto_pass"
    }
}
