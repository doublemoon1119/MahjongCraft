package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [BeginRoundPreparationUseCase] 的局前準備設定與契約測試。 */
class BeginRoundPreparationUseCaseTest {
    /** 規則沒有登記準備流程時，不修改對局。 */
    @Test
    fun `no resolver leaves game unchanged`() = runTest {
        val repository = FakeGameRepository()
        val game = game()
        repository.setGame(game)

        invoke(repository, RoundPreparationResolverRegistry())

        assertEquals(game, repository.getGame(game.tableState.id))
    }

    /** 純自動準備步驟不需要玩家思考時間設定。 */
    @Test
    fun `automatic preparation may start without timing configuration`() = runTest {
        val repository = FakeGameRepository()
        val game = game()
        repository.setGame(game)
        val resolvers = RoundPreparationResolverRegistry().apply { register(AutoResolver) }

        invoke(repository, resolvers)

        assertEquals("test:auto", repository.getGame(game.tableState.id)?.pendingRoundPreparation?.stepId)
    }

    /** 缺少玩家準備時間時，以發現問題的對局快照中止，不保留可繼續遊玩的對局。 */
    @Test
    fun `player preparation without timing configuration aborts using the transaction basis`() = runTest {
        val repository = FakeGameRepository()
        val game = game()
        repository.setGame(game)
        val resolvers = RoundPreparationResolverRegistry().apply { register(PlayerResolver) }
        var abortedBasis: Game? = null
        val abortHandler = GameAbortHandler { expected, _, _ ->
            abortedBasis = expected
            repository.removeTableState(expected.id)
            true
        }

        assertFailsWith<RuleContractViolationException> {
            invoke(repository, resolvers, abortHandler)
        }
        assertEquals(game, abortedBasis)
        assertNull(repository.getGame(game.tableState.id))
    }

    /** 有明確準備時間時，可建立需要玩家輸入的步驟。 */
    @Test
    fun `player preparation with timing configuration starts successfully`() = runTest {
        val repository = FakeGameRepository()
        val game = game(flowConfig = GameFlowConfig(roundPreparation = RoundPreparationConfig(30)))
        repository.setGame(game)
        val resolvers = RoundPreparationResolverRegistry().apply { register(PlayerResolver) }

        invoke(repository, resolvers)

        assertEquals("test:player", repository.getGame(game.tableState.id)?.pendingRoundPreparation?.stepId)
    }

    /**
     * 以指定 resolver 和中止處理器執行局前準備開始流程。
     *
     * @param repository 保存測試對局的 repository。
     * @param resolvers 提供測試規則準備流程的 registry。
     * @param abortHandler 契約違反時接收中止請求的處理器。
     */
    private suspend fun invoke(
        repository: FakeGameRepository,
        resolvers: RoundPreparationResolverRegistry,
        abortHandler: GameAbortHandler = GameAbortHandler { _, _, _ -> true },
    ) {
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        BeginRoundPreparationUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolvers,
            snapshotSynchronizer = GameSnapshotSynchronizer(
                repository,
                FakeGameSnapshotRepository(),
                GameVisibilityPolicyImpl(moduleRegistry),
            ),
            abortGameHandler = abortHandler,
        )(repository.getGame(testGameId)!!.tableState.id)
    }

    /**
     * 建立使用內建日麻規則的測試對局。
     *
     * @param flowConfig 測試對局採用的流程設定。
     * @return 測試用對局。
     */
    private fun game(flowConfig: GameFlowConfig = GameFlowConfig()): Game = Game(
        tableState = FakeTableStateFactory.create(id = testGameId, config = RiichiRuleConfig()),
        flowConfig = flowConfig,
    )

    private companion object {
        /** 測試對局共用的桌面識別碼。 */
        val testGameId: Uuid = Uuid.random()

        /** 只建立自動步驟的測試解析器。 */
        object AutoResolver : RoundPreparationResolver {
            override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

            override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>) = PendingRoundPreparation("test:auto", 0, emptyMap())

            override fun resolve(tableState: TableState, preparation: PendingRoundPreparation, ruleModule: MahjongRuleModule<*>) = RoundPreparationResolution(tableState, null)
        }

        /** 建立需要玩家輸入的測試解析器。 */
        object PlayerResolver : RoundPreparationResolver {
            override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

            override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>) = PendingRoundPreparation(
                "test:player",
                0,
                mapOf(tableState.players.first().id to RoundPreparationInputSpec.Confirmation),
            )

            override fun resolve(tableState: TableState, preparation: PendingRoundPreparation, ruleModule: MahjongRuleModule<*>) = RoundPreparationResolution(tableState, null)
        }
    }
}
