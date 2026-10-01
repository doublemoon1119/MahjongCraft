package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.mojang.brigadier.tree.CommandNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import net.minecraft.server.command.ServerCommandSource
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 驗證歷史 debug 指令樹的 literal、引數限制與查詢子指令。 */
class FabricDebugHistoryCommandTest {
    /** 指令樹建構不執行控制器或 writer 的外部工作。 */
    @Test
    fun `history command exposes generation status and cancel`() {
        val store = AuthoritativeStateStore()
        val writer = FabricHistoryOutboxWriter(
            store = store,
            registries = buildBuiltInPersistenceRegistries(),
            json = Json,
            dispatchers = TestDispatchers,
            moduleRegistry = MahjongModuleRegistryImpl(),
            locations = TableLocationRegistry(),
            configState = MinecraftServerConfigState(),
            retentionCoordinator = HistoryRetentionCoordinator(),
        )
        val command = FabricDebugHistoryCommand(
            controller = HistoryGenerationController(
                writer = writer,
                runtimeFactory = HistoryGenerationRuntimeFactory { error("Unexpected runtime creation") },
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
        assertEquals(setOf("generate", "status", "cancel"), node.children.map(CommandNode<ServerCommandSource>::getName).toSet())
        assertNotNull(node.getChild("status")?.command)
        assertNotNull(node.getChild("cancel")?.command)
        val generate = node.getChild("generate")
        assertNotNull(generate)
        assertNotNull(generate.getChild("scenario"))
        assertEquals(setOf("count"), generate.getChild("scenario")!!.children.map(CommandNode<ServerCommandSource>::getName).toSet())
    }

    /** 測試使用的應用協程作用域。 */
    private class TestAppScope : AppCoroutineScope {
        override val coroutineContext: CoroutineContext = Job() + Dispatchers.Default
        override fun cancel() {
            coroutineContext[Job]?.cancel()
        }
        override suspend fun shutdown(timeoutMillis: Long) {
            coroutineContext[Job]?.cancel()
        }
    }

    /** 測試使用的 dispatcher 集合。 */
    private object TestDispatchers : CoroutineDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Default
    }
}
