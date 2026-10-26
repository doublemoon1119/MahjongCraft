package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
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
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementCondition
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementGrantExecutor
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementStatisticIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.BuiltInAchievementIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.StatisticThreshold
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

        service.handleForTest(tsumo(winner.id), isSessionActive = { active })
        assertTrue(gateway.granted.isEmpty())
        active = false
        runCurrent()

        assertTrue(gateway.granted.isEmpty())
    }

    /** 判定出的成果交給 gateway。 */
    @Test
    fun `detected achievements are granted`() {
        val gateway = RecordingGateway()

        service(gateway).handleForTest(tsumo(winner.id))

        assertEquals(listOf(winner.id), gateway.granted.map { it.playerId })
        assertTrue(BuiltInAchievementIds.SELF_DRAW_WIN in gateway.granted.single().achievementIds)
    }

    /** 規則成果與通用成果會等到同一公開批次後合併成一次授予。 */
    @Test
    fun `rule and generic achievements are merged after batch delivery`() {
        val gateway = ExecutorGateway(
            condition = AchievementCondition(
                achievementIds = setOf(BuiltInAchievementIds.WIN, BuiltInAchievementIds.FIRST_PLACE),
                statistic = StatisticThreshold(
                    AchievementStatisticIds.WINS,
                    1,
                ),
            ),
        )
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(object : GameAchievementResolver {
                /** 測試使用的日麻規則模組識別碼。 */
                override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

                /** 提供一項規則成果，與通用自摸成果合併。 */
                override fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> = mapOf(
                    winner.id to setOf(BuiltInAchievementIds.FIRST_PLACE),
                )
            })
        }
        val service = service(gateway, registry)
        val facts = tsumo(winner.id)
        assertTrue(gateway.granted.isEmpty())
        service.consume(checkNotNull(project(facts)))

        assertEquals(1, gateway.granted.size)
        assertEquals(1, gateway.statistics[AchievementStatisticIds.WINS])
        assertTrue(gateway.conditionMatched)
        assertEquals(
            setOf(BuiltInAchievementIds.SELF_DRAW_WIN, BuiltInAchievementIds.WIN, BuiltInAchievementIds.FIRST_PLACE),
            gateway.granted.single().achievementIds,
        )
    }

    /** 排程授予時保存成果快照，resolver 修改原集合也不會改變等待授予的內容。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `queued rule achievements keep their snapshot`() = runTest {
        val ruleAchievements = linkedSetOf(BuiltInAchievementIds.FIRST_PLACE)
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(object : GameAchievementResolver {
                /** 測試使用的日麻規則模組識別碼。 */
                override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

                /** 回傳可變的測試成果集合。 */
                override fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> = mapOf(winner.id to ruleAchievements)
            })
        }
        val gateway = RecordingGateway()
        val service = service(gateway, registry, TestCoroutineDispatchers(main = StandardTestDispatcher(testScheduler)))
        val facts = tsumo(winner.id)
        service.consume(checkNotNull(project(facts)))
        assertTrue(gateway.granted.isEmpty())
        ruleAchievements.clear()
        runCurrent()

        assertTrue(BuiltInAchievementIds.FIRST_PLACE in gateway.granted.single().achievementIds)
    }

    /** 即使沒有開局事件，批次玩家快照仍會排除 AI。 */
    @Test
    fun `ai players are excluded without a match started cache`() {
        val ai = game.tableState.players[0]
        val human = game.tableState.players[1]
        val aiGame = game.copy(aiPlayerStrategyKeys = mapOf(ai.id to "test:ai"))
        val gateway = RecordingGateway()
        val service = service(gateway)
        val facts = facts(
            HistoryFact.WinSettled(
                BuiltInRoundOutcomeIds.TSUMO,
                listOf(HistoryWinDetails(ai.id, emptyList()), HistoryWinDetails(human.id, emptyList())),
            ),
            aiGame,
        )

        service.handleForTest(facts)

        assertEquals(listOf(human.id), gateway.granted.map { it.playerId })
    }

    /** 離線授予會略過且不會在之後自動補發。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `offline achievement is not replayed`() = runTest {
        val gateway = SwitchingGateway()
        val dispatchers = TestCoroutineDispatchers(main = StandardTestDispatcher(testScheduler))
        val service = service(gateway, dispatchers = dispatchers)
        val facts = tsumo(winner.id)

        service.handleForTest(facts)
        runCurrent()
        gateway.online = true
        runCurrent()

        assertTrue(gateway.granted.isEmpty())
        service.handleForTest(tsumo(winner.id))
        runCurrent()
        assertEquals(1, gateway.granted.size)
    }

    /** 一位玩家授予失敗時，同一筆事實的其他玩家仍會授予。 */
    @Test
    fun `a failed grant does not stop other players`() {
        val dealer = game.tableState.players[1]
        val gateway = RecordingGateway(failingPlayerId = winner.id)

        service(gateway).handleForTest(
            facts(HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(HistoryWinDetails(winner.id, emptyList())), listOf(dealer.id))),
        )

        assertEquals(listOf(dealer.id), gateway.granted.map { it.playerId })
    }

    /** 事件批次會交給規則成果判定器。 */
    @Test
    fun `excluded matches are not detected`() {
        var resolverCalls = 0
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(object : GameAchievementResolver {
                /** 此測試使用的立直規則模組識別碼。 */
                override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

                /** 計算呼叫次數並回傳空的成果集合。 */
                override fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> = emptyMap<Uuid, Set<String>>().also { resolverCalls++ }
            })
        }
        val service = service(RecordingGateway(), registry)
        service.handleForTest(tsumo(winner.id))
        assertEquals(1, resolverCalls)
    }

    /** 批次判定失敗不授予成果，之後的批次照常處理。 */
    @Test
    fun `a failed detection does not stop later facts`() {
        val gateway = RecordingGateway()
        var failing = true
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(object : GameAchievementResolver {
                /** 此測試使用的立直規則模組識別碼。 */
                override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

                /** 依測試狀態模擬判定失敗或回傳空的成果集合。 */
                override fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> = if (failing) error("resolver failed") else emptyMap()
            })
        }
        val service = service(gateway, registry)

        service.handleForTest(tsumo(winner.id))
        failing = false
        service.handleForTest(tsumo(winner.id))

        assertEquals(1, gateway.granted.size)
    }

    /**
     * 建立可替換授予端與成果判定器的測試服務。
     *
     * @param gateway 接收成果授予的測試端。
     * @param registry 規則成果判定器登記表。
     * @param dispatchers 控制授予工作的測試排程器。
     * @return 使用內建規則模組的成果服務。
     */
    private fun service(
        gateway: AchievementGrantGateway,
        registry: GameAchievementResolverRegistryImpl = GameAchievementResolverRegistryImpl(),
        dispatchers: TestCoroutineDispatchers = TestCoroutineDispatchers(),
    ): FabricAchievementService = FabricAchievementService(
        scope = createTestAppCoroutineScope(dispatchers),
        dispatchers = dispatchers,
        resolverRegistry = registry,
        gateway = gateway,
        failurePolicy = FabricExtensionFailurePolicy(),
    )

    /**
     * 建立指定玩家自摸的已提交事實。
     *
     * @param winnerId 贏家識別碼。
     * @param sourceGame 交易前後使用的對局快照。
     * @return 自摸交易事實。
     */
    private fun tsumo(winnerId: Uuid, sourceGame: Game = game) = facts(
        HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(HistoryWinDetails(winnerId, emptyList()))),
        sourceGame,
    )

    /**
     * 將單一事實包成測試用的交易通知。
     *
     * @param fact 要交付的歷史事實。
     * @param sourceGame 交易前後使用的對局快照。
     * @return 包含指定事實的交易通知。
     */
    private fun facts(fact: HistoryFact, sourceGame: Game = game) = CommittedGameFacts(
        venueId = sourceGame.id,
        previousGame = sourceGame,
        game = sourceGame,
        facts = listOf(HistoryEventDraft(null, fact)),
    )

    /** 以真正的事件投影將測試交易交給成果服務。 */
    private fun FabricAchievementService.handleForTest(
        facts: CommittedGameFacts,
        isSessionActive: () -> Boolean = { true },
    ) {
        project(facts)?.let { consume(it, isSessionActive) }
    }

    /** 將測試交易投影成成果服務使用的公開事件批次。 */
    private fun project(facts: CommittedGameFacts): CommittedMatchEventBatch? = GameEventProjector(
        moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() },
    ).projectBatch(facts)

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

    /** 模擬玩家在線切換，驗證離線成果不會補發。 */
    private class SwitchingGateway : AchievementGrantGateway {
        /** 玩家目前是否在線。 */
        var online: Boolean = false

        /** 成功收到的授予。 */
        val granted = mutableListOf<PlayerAchievements>()

        /** 玩家離線時略過，重新上線後只接受新的交易。 */
        override fun grant(achievements: PlayerAchievements): Boolean {
            if (!online) return false
            granted += achievements
            return true
        }
    }

    /** 使用實際成果執行器驗證統計累加與複合條件。 */
    private class ExecutorGateway(
        private val condition: AchievementCondition,
    ) : AchievementGrantGateway {
        /** 收到的成果授予。 */
        val granted = mutableListOf<PlayerAchievements>()

        /** 各統計目前的值。 */
        val statistics = mutableMapOf<String, Int>()

        /** 複合條件是否在統計累加後成立。 */
        var conditionMatched: Boolean = false

        /** 使用共用成果執行器處理一次授予。 */
        override fun grant(achievements: PlayerAchievements): Boolean {
            AchievementGrantExecutor.grant(
                achievements = achievements,
                incrementStatistic = { statisticId -> statistics[statisticId] = statistics.getOrDefault(statisticId, 0) + 1 },
                triggerCriterion = {
                    conditionMatched = condition.matches(achievements) { statisticId -> statistics.getValue(statisticId) }
                },
            )
            granted += achievements
            return true
        }
    }
}
