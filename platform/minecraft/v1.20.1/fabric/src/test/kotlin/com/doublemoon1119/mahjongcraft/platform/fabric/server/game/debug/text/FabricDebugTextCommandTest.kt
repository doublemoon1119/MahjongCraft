package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigLocation
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigPathProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigTomlCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.mojang.brigadier.tree.CommandNode
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證訊息排版預覽子指令樹的 literal 與參數結構。 */
class FabricDebugTextCommandTest {
    private val command = FabricDebugTextCommand(
        feedbackPublisher = RecordingFeedbackPublisher(),
        serverConfigManager = FabricServerConfigManager(
            MinecraftServerConfigState(),
            MinecraftServerConfigTomlCodec(),
            UnusedPathProvider,
        ),
        json = Json,
        networkRegistries = DefaultNetworkDtoRegistries(),
    )

    /** `hovered_text` 保留六個訊息排版預覽節點。 */
    @Test
    fun `hovered text keeps every preview literal`() {
        val node = command.buildHoveredTextCommand().build()

        assertEquals("hovered_text", node.name)
        assertEquals(
            setOf(
                "exhaustive_draw_settlement",
                "win_settlement",
                "match_settlement",
                "game_created_location",
                "game_config",
                "server_config",
            ),
            node.children.map(CommandNode<ServerCommandSource>::getName).toSet(),
        )
    }

    /** `game_config` 以外的節點都直接執行，沒有額外子節點。 */
    @Test
    fun `leaf previews execute without further arguments`() {
        val children = command.buildHoveredTextCommand().build().children.associateBy { it.name }

        listOf(
            "exhaustive_draw_settlement",
            "win_settlement",
            "match_settlement",
            "game_created_location",
            "server_config",
        ).forEach { name ->
            val child = children.getValue(name)
            assertNotNull(child.command, "$name executes directly")
            assertEquals(emptySet(), child.children.map(CommandNode<ServerCommandSource>::getName).toSet(), "$name has no child node")
        }
    }

    /** `game_config` 需要指定變體，本身不可直接執行。 */
    @Test
    fun `game config requires an explicit variant`() {
        val gameConfig = command.buildHoveredTextCommand().build().children.single { it.name == "game_config" }

        assertNull(gameConfig.command, "game_config is not executable on its own")
        assertEquals(
            setOf("show", "changed", "unchanged"),
            gameConfig.children.map(CommandNode<ServerCommandSource>::getName).toSet(),
        )
        gameConfig.children.forEach { variant ->
            assertNotNull(variant.command, "${variant.name} executes directly")
        }
    }

    /** 記錄收到的回饋，供建構 command 使用。 */
    private class RecordingFeedbackPublisher : MinecraftPlayerFeedbackPublisher {
        /** 測試不驗證回饋內容，僅需要一個可用的實作。 */
        override fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback) = Unit
    }

    /** 訊息排版測試不經由 Minecraft server 解析設定路徑。 */
    private object UnusedPathProvider : FabricServerConfigPathProvider {
        /** 若測試誤用 server attach，立即回報測試設定錯誤。 */
        override fun get(server: MinecraftServer): FabricServerConfigLocation = error("Unexpected server path lookup")
    }
}
