package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.GameEventExclusions
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.BuiltInAchievementIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [FabricAchievementService] 的成果授予、排除場次與失敗隔離。 */
class FabricAchievementServiceTest {
    /** 提供四個座位與預設立直規則的測試對局。 */
    private val game = Game(
        FakeTableStateFactory.create(
            players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) },
            config = RiichiRuleConfig(),
        ),
        GameFlowConfig(),
    )

    /** 測試和牌事實使用的贏家。 */
    private val winner = game.tableState.players.first()

    /** 授予排程後來源 session 結束時，不把舊成果授予下一個世界。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `queued grants are rejected after their source session ends`() = runTest {
        val gateway = RecordingGateway()
        val dispatchers = TestCoroutineDispatchers(main = StandardTestDispatcher(testScheduler))
        val service = service(gateway = gateway, dispatchers = dispatchers)
        var active = true

        service.handle(tsumo(winner.id)) { active }
        assertTrue(gateway.granted.isEmpty())
        active = false
        runCurrent()

        assertTrue(gateway.granted.isEmpty())
    }

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
        val exclusions = GameEventExclusions().also { it.exclude(game.matchId) }
        val service = service(gateway, exclusions = exclusions)

        service.handle(tsumo(winner.id))

        assertTrue(gateway.granted.isEmpty())
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

    /** 排除的場次在判定前就略過，不呼叫任何判定。 */
    @Test
    fun `excluded matches are not detected`() {
        var resolverCalls = 0
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(object : GameAchievementResolver {
                /** 此測試使用的立直規則模組識別碼。 */
                override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

                /**
                 * 計算呼叫次數並回傳空的成果集合。
                 *
                 * @param facts 已提交的對局事實。
                 * @return 不含授予成果的空集合。
                 */
                override fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>> = emptyMap<Uuid, Set<String>>().also { resolverCalls++ }
            })
        }
        val exclusions = GameEventExclusions().also { it.exclude(game.matchId) }
        val service = service(RecordingGateway(), registry, exclusions)

        service.handle(tsumo(winner.id))

        assertEquals(0, resolverCalls)
    }

    /** 判定失敗的事實不授予成果，之後的事實照常處理。 */
    @Test
    fun `a failed detection does not stop later facts`() {
        val gateway = RecordingGateway()
        var failing = true
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(object : GameAchievementResolver {
                /** 此測試使用的立直規則模組識別碼。 */
                override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

                /**
                 * 依測試狀態模擬判定失敗或回傳空的成果集合。
                 *
                 * @param facts 已提交的對局事實。
                 * @return 不含授予成果的空集合；失敗狀態下拋出測試例外。
                 */
                override fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>> = if (failing) error("resolver failed") else emptyMap()
            })
        }
        val service = service(gateway, registry)

        service.handle(tsumo(winner.id))
        failing = false
        service.handle(tsumo(winner.id))

        assertEquals(1, gateway.granted.size)
    }

    /**
     * 建立可替換授予端與成果判定器的測試服務。
     *
     * @param gateway 接收成果授予的測試端。
     * @param registry 規則成果判定器登記表。
     * @param exclusions 不授予成果的場次集合。
     * @param dispatchers 控制授予工作的測試排程器。
     * @return 使用內建規則模組的成果服務。
     */
    private fun service(
        gateway: AchievementGrantGateway,
        registry: GameAchievementResolverRegistryImpl = GameAchievementResolverRegistryImpl(),
        exclusions: GameEventExclusions = GameEventExclusions(),
        dispatchers: TestCoroutineDispatchers = TestCoroutineDispatchers(),
    ): FabricAchievementService = FabricAchievementService(
        scope = createTestAppCoroutineScope(dispatchers),
        dispatchers = dispatchers,
        moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() },
        resolverRegistry = registry,
        gateway = gateway,
        exclusions = exclusions,
    )

    /**
     * 建立指定玩家自摸的已提交事實。
     *
     * @param winnerId 贏家識別碼。
     * @return 使用固定測試對局的自摸事實。
     */
    private fun tsumo(winnerId: Uuid) = facts(
        HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(HistoryWinDetails(winnerId, emptyList()))),
    )

    /**
     * 將單一事實包成測試用的交易通知。
     *
     * @param fact 要交付的歷史事實。
     * @return 前後皆引用測試對局的交易通知。
     */
    private fun facts(fact: HistoryFact) = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = listOf(HistoryEventDraft(null, fact)),
    )

    /**
     * 記錄成果授予內容，並可模擬指定玩家授予失敗。
     *
     * @property failingPlayerId 授予時拋出例外的玩家；null 表示皆成功。
     */
    private class RecordingGateway(private val failingPlayerId: Uuid? = null) : AchievementGrantGateway {
        /** 成功接收的成果，依授予順序排列。 */
        val granted = mutableListOf<PlayerAchievements>()

        /**
         * 記錄成果，並在指定玩家匹配時模擬授予失敗。
         *
         * @param achievements 欲授予玩家的成果集合。
         * @return 成功接收時回傳 true。
         */
        override fun grant(achievements: PlayerAchievements): Boolean {
            check(achievements.playerId != failingPlayerId) { "grant failed" }
            granted += achievements
            return true
        }
    }
}
