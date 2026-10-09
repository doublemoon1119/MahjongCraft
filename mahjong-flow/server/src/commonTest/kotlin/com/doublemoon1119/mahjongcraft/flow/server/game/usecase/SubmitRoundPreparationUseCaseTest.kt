package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [SubmitRoundPreparationUseCase] 的單元測試。 */
class SubmitRoundPreparationUseCaseTest {
    private val playerId = Uuid.random()

    /**
     * 最後一位參與者確認後，步驟由規則解析並結束。
     *
     * 以目前的遊戲為預期遊戲執行，確認命令只寫入這一局一次（AI 命令的條件式提交契約）。
     */
    @Test
    fun `the last confirmation resolves the step`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val resolvers = RoundPreparationResolverRegistry().apply { register(ConfirmationResolver) }
        val useCase = SubmitRoundPreparationUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = resolvers,
            snapshotSynchronizer = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(moduleRegistry)),
        )
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            config = RiichiRuleConfig(),
        )
        repository.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                pendingRoundPreparation = PendingRoundPreparation(
                    stepId = "test:confirm",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(playerId to RoundPreparationInputSpec.Confirmation),
                ),
            ),
        )

        val result = repository.runAsSingleWrite(table.id) { useCase(table.id, playerId, RoundPreparationSubmission.Confirmed) }

        assertIs<Outcome.Success<Unit>>(result)
        assertNull(repository.getGame(table.id)?.pendingRoundPreparation)
        assertEquals(table, repository.getGame(table.id)?.tableState)
    }

    /** 收齊確認後不改桌況、也沒有下一步的解析器。 */
    private object ConfirmationResolver : RoundPreparationResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>): PendingRoundPreparation? = null

        override fun resolve(
            tableState: TableState,
            preparation: PendingRoundPreparation,
            ruleModule: MahjongRuleModule<*>,
        ): RoundPreparationResolution = RoundPreparationResolution(tableState, nextStep = null)
    }
}
