package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證擴充命令依各執行環境的流程服務建立 handler 並執行。 */
class ExtensionGameCommandExecutorTest {
    private val gameId = Uuid.random()
    private val playerId = Uuid.random()

    /** 同一份登記可供多個執行環境使用，每個環境的 handler 只拿到自己的流程服務。 */
    @Test
    fun `each executor builds handlers with its own context`() = runTest {
        val receivedContexts = mutableListOf<ExtensionGameCommandContext>()
        val registry = recordingRegistry(receivedContexts)
        val first = context()
        val second = context()

        ExtensionGameCommandExecutor(registry, first).execute(gameId, playerId, TestCommand)
        ExtensionGameCommandExecutor(registry, second).execute(gameId, playerId, TestCommand)

        assertEquals(2, receivedContexts.size)
        assertSame(first, receivedContexts[0])
        assertSame(second, receivedContexts[1])
    }

    /** handler 在第一次執行命令時才建立，之後重複使用同一個。 */
    @Test
    fun `handlers are created on first execution and reused`() = runTest {
        val receivedContexts = mutableListOf<ExtensionGameCommandContext>()
        val executor = ExtensionGameCommandExecutor(recordingRegistry(receivedContexts), context())

        assertTrue(receivedContexts.isEmpty())
        executor.execute(gameId, playerId, TestCommand)
        executor.execute(gameId, playerId, TestCommand)

        assertEquals(1, receivedContexts.size)
    }

    /** 未登記的命令安全回傳不支援。 */
    @Test
    fun `unknown command is unsupported`() = runTest {
        val executor = ExtensionGameCommandExecutor(ExtensionGameCommandExecutorRegistry().apply { freeze() }, context())

        val result = executor.execute(gameId, playerId, TestCommand)

        assertEquals(Outcome.Error(GameError.UnsupportedAction(gameId, playerId)), result)
    }

    /** 註冊表尚未凍結時拒絕建立 handler，避免之後登記的命令不在執行環境中。 */
    @Test
    fun `executing before freeze is rejected`() = runTest {
        val registry = ExtensionGameCommandExecutorRegistry().apply {
            register(TestCommand::class) { _ -> successHandler() }
        }

        assertFailsWith<IllegalStateException> {
            ExtensionGameCommandExecutor(registry, context()).execute(gameId, playerId, TestCommand)
        }
    }

    /** 建立會記錄收到哪個 context 的已凍結註冊表。 */
    private fun recordingRegistry(receivedContexts: MutableList<ExtensionGameCommandContext>) = ExtensionGameCommandExecutorRegistry().apply {
        register(TestCommand::class) { context ->
            receivedContexts += context
            successHandler()
        }
        freeze()
    }

    /** 一律成功的測試 handler。 */
    private fun successHandler() = object : ExtensionGameCommandHandler<TestCommand> {
        override suspend fun execute(
            gameId: Uuid,
            playerId: Uuid,
            command: TestCommand,
        ): Outcome<Unit, GameError> = Outcome.Success(Unit)
    }

    /** 建立一個獨立執行環境的流程服務。 */
    private fun context(): ExtensionGameCommandContext {
        val gameRepository = FakeGameRepository()
        return ExtensionGameCommandContext(
            gameRepository = gameRepository,
            moduleRegistry = MahjongModuleRegistryImpl(),
            snapshotSynchronizer = GameSnapshotSynchronizer(gameRepository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(MahjongModuleRegistryImpl().apply { registerBundledRuleModules() })),
            handSortPreferenceStore = HandSortPreferenceStore(),
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry(),
            eventPublisher = FakeGameEventPublisher(),
            presentationPublisher = FakeGamePresentationPublisher(),
        )
    }

    /** 測試用擴充命令。 */
    private data object TestCommand : ExtensionGameCommand
}
