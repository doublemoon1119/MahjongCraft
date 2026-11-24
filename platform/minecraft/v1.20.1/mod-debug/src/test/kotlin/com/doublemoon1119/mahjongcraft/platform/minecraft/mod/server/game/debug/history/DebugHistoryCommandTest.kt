package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayPresentationNotes
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.HistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture.HistoryFixtureRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.mojang.brigadier.tree.CommandNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import net.minecraft.commands.CommandSourceStack
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration

/** 驗證歷史 debug 指令樹的 literal、引數限制與查詢子指令。 */
class DebugHistoryCommandTest {
    /** 指令樹建構不執行控制器或 writer 的外部工作。 */
    @Test
    fun `history command exposes generation status and cancel`() {
        val store = AuthoritativeStateStore()
        val writer = HistoryOutboxWriter(
            store = store,
            registries = bundledPersistenceRegistries(),
            dispatchers = TestDispatchers,
            moduleRegistry = MahjongModuleRegistryImpl(),
            locations = TableLocationRegistry(),
            presentationNotes = ReplayPresentationNotes.NONE,
            configState = MinecraftServerConfigState(),
            retentionCoordinator = HistoryRetentionCoordinator(),
        )
        val command = DebugHistoryCommand(
            controller = HistoryGenerationController(
                writer = writer,
                runtimeFactory = HistoryGenerationRuntimeFactory { error("Unexpected runtime creation") },
                fixtureRuntimeFactory = HistoryFixtureRuntimeFactory { error("Unexpected runtime creation") },
                store = store,
                configState = MinecraftServerConfigState(),
                scope = TestAppScope(),
                dispatchers = TestDispatchers,
            ),
            writer = writer,
            scope = TestAppScope(),
            dispatchers = TestDispatchers,
        )

        val node = command.build().build()

        assertEquals("history", node.name)
        assertEquals(setOf("generate", "fixture", "status", "cancel"), node.children.map(CommandNode<CommandSourceStack>::getName).toSet())
        assertNotNull(node.getChild("status")?.command)
        assertNotNull(node.getChild("cancel")?.command)
        val generate = node.getChild("generate")
        assertNotNull(generate)
        assertNotNull(generate.getChild("scenario"))
        assertEquals(setOf("count"), generate.getChild("scenario")!!.children.map(CommandNode<CommandSourceStack>::getName).toSet())
    }

    /** 測試使用的應用協程作用域。 */
    private class TestAppScope : AppCoroutineScope {
        /** 測試作用域使用的協程工作與預設 dispatcher。 */
        override val coroutineContext: CoroutineContext = Job() + Dispatchers.Default

        /** 取消測試作用域中的工作。 */
        override fun cancel() {
            coroutineContext[Job]?.cancel()
        }

        /** 測試不等待其他工作，直接取消作用域。 */
        override suspend fun shutdown(timeout: Duration) {
            coroutineContext[Job]?.cancel()
        }
    }

    /** 測試使用的 dispatcher 集合。 */
    private object TestDispatchers : CoroutineDispatchers {
        /** CPU 工作使用預設 dispatcher。 */
        override val default: CoroutineDispatcher = Dispatchers.Default

        /** 檔案工作使用 I/O dispatcher。 */
        override val io: CoroutineDispatcher = Dispatchers.IO

        /** 主執行緒工作使用預設 dispatcher。 */
        override val main: CoroutineDispatcher = Dispatchers.Default

        /** AI 決策工作使用預設 dispatcher。 */
        override val aiDecision: CoroutineDispatcher = Dispatchers.Default

        /** 測試宣告的 AI 決策平行工作數。 */
        override val aiDecisionParallelism = 1
    }
}
