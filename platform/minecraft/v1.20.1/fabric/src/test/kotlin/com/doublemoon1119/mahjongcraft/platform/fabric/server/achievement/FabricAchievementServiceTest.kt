package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.BuiltInAchievementIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [FabricAchievementService] 的成果授予、排除場次與失敗隔離。 */
class FabricAchievementServiceTest {
    private val game = Game(
        FakeTableStateFactory.create(
            players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) },
            config = RiichiRuleConfig(),
        ),
        GameFlowConfig(),
    )
    private val winner = game.tableState.players.first()

    /** 判定出的成果交給 gateway。 */
    @Test
    fun `detected achievements are granted`() {
        val gateway = RecordingGateway()

        service(gateway).handle(tsumo(winner.id))

        assertEquals(listOf(winner.id), gateway.granted.map { it.playerId })
        assertTrue(BuiltInAchievementIds.SELF_DRAW_WIN in gateway.granted.single().achievementIds)
    }

    /** 被排除的場次不授予任何成果。 */
    @Test
    fun `excluded matches are not granted`() {
        val gateway = RecordingGateway()
        val service = service(gateway)
        service.excludeMatch(game.matchId)

        service.handle(tsumo(winner.id))

        assertTrue(gateway.granted.isEmpty())
    }

    /** 換 session 後排除的場次會清除。 */
    @Test
    fun `stopping the session clears excluded matches`() {
        val gateway = RecordingGateway()
        val service = service(gateway)
        service.excludeMatch(game.matchId)

        service.stopSession()
        service.handle(tsumo(winner.id))

        assertEquals(1, gateway.granted.size)
    }

    /** 一位玩家授予失敗時，同一筆事實的其他玩家仍會授予。 */
    @Test
    fun `a failed grant does not stop other players`() {
        val dealer = game.tableState.players[1]
        val gateway = RecordingGateway(failingPlayerId = winner.id)

        service(gateway).handle(
            facts(HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(HistoryWinDetails(winner.id, emptyList())), listOf(dealer.id))),
        )

        assertEquals(listOf(dealer.id), gateway.granted.map { it.playerId })
    }

    /** 判定失敗的事實不授予成果，之後的事實照常處理。 */
    @Test
    fun `a failed detection does not stop later facts`() {
        val gateway = RecordingGateway()
        var failing = true
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(object : GameAchievementResolver {
                override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

                override fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>> = if (failing) error("resolver failed") else emptyMap()
            })
        }
        val service = service(gateway, registry)

        service.handle(tsumo(winner.id))
        failing = false
        service.handle(tsumo(winner.id))

        assertEquals(1, gateway.granted.size)
    }

    private fun service(
        gateway: AchievementGrantGateway,
        registry: GameAchievementResolverRegistryImpl = GameAchievementResolverRegistryImpl(),
    ): FabricAchievementService {
        val dispatchers = TestCoroutineDispatchers()
        return FabricAchievementService(
            scope = createTestAppCoroutineScope(dispatchers),
            dispatchers = dispatchers,
            store = AuthoritativeStateStore(),
            moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() },
            resolverRegistry = registry,
            gateway = gateway,
        )
    }

    private fun tsumo(winnerId: Uuid) = facts(
        HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(HistoryWinDetails(winnerId, emptyList()))),
    )

    private fun facts(fact: HistoryFact) = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = listOf(HistoryEventDraft(null, fact)),
    )

    /** 記錄授予內容的 gateway；指定玩家授予時拋出例外。 */
    private class RecordingGateway(private val failingPlayerId: Uuid? = null) : AchievementGrantGateway {
        val granted = mutableListOf<PlayerAchievements>()

        override fun grant(achievements: PlayerAchievements): Boolean {
            check(achievements.playerId != failingPlayerId) { "grant failed" }
            granted += achievements
            return true
        }
    }
}
