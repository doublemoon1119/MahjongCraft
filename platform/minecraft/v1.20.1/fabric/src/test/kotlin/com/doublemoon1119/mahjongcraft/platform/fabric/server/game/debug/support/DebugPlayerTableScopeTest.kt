package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.AchievementGrantGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.FabricAchievementService
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 debug 指令共用牌桌範圍對場次紀錄的排除。 */
class DebugPlayerTableScopeTest {
    /** 用過 debug 指令的場次停止歷史紀錄，之後的事實不再追加。 */
    @Test
    fun `excluding the current match stops its history recording`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val tableState = FakeTableStateFactory.create()
        val game = Game(tableState, GameFlowConfig())
        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (game.id to game)),
                result = Unit,
                historyDraftsByTableId = mapOf(
                    game.id to listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(tableState, game.flowConfig))),
                ),
            )
        }
        val dispatchers = TestCoroutineDispatchers()
        val scope = createTestAppCoroutineScope(dispatchers)
        val playerTableScope = DebugPlayerTableScope(
            membershipRepository = PlayerMembershipRepositoryImpl(),
            gameRepository = GameRepositoryImpl(store),
            achievementService = FabricAchievementService(
                scope = scope,
                dispatchers = dispatchers,
                store = store,
                moduleRegistry = MahjongModuleRegistryImpl(),
                resolverRegistry = GameAchievementResolverRegistryImpl(),
                gateway = UnusedGateway,
            ),
            stateStore = store,
            scope = scope,
            dispatchers = dispatchers,
        )

        playerTableScope.excludeCurrentMatch(game.id)

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED, recording.decisionsByMatchId[game.matchId])
        assertEquals(listOf(1L), recording.pendingEvents.map { it.sequence })
        assertEquals(2L, recording.firstMissingSequenceByMatchId[game.matchId])
    }

    /** 排除場次不授予任何成果。 */
    private object UnusedGateway : AchievementGrantGateway {
        override fun grant(achievements: PlayerAchievements): Boolean = error("Unexpected achievement grant")
    }
}
