package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigTomlCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [FabricServerConfigManager] 的預設建立與交易式熱重載測試。 */
class FabricServerConfigManagerTest {
    /** 缺少設定檔時應建立帶完整註解的 template 並套用預設值。 */
    @Test
    fun `test initialize creates annotated default config`() = withFixture { fixture ->
        val result = assertIs<MinecraftServerConfigUpdateResult.Success>(fixture.manager.attach(fixture.location))
        val content = Files.readString(fixture.path)

        assertTrue(result.createdDefaultFile)
        assertTrue(content.contains("Controls what happens to a player's seat"))
        assertTrue(content.contains("<world save>/serverconfig/mahjongcraft.toml"))
        assertTrue(content.contains("<server directory>/config/mahjongcraft/server.toml"))
        assertTrue(content.contains("Games that have already started always keep"))
        assertTrue(content.contains("allow_and_terminate"))
        assertTrue(content.contains("remove_waiting_room"))
        assertTrue(content.contains("[mahjong-tile]"))
        assertTrue(content.contains("physical-collision-enabled = true"))
        assertTrue(content.contains("Raycasting, right-click interaction, rendering, saving, and HUD targeting"))
        assertEquals(MinecraftServerConfig(), fixture.state.current)
    }

    /** 有效 reload 應完整替換記憶體設定並反映於標準 TOML。 */
    @Test
    fun `test reload replaces effective config`() = withFixture { fixture ->
        fixture.manager.attach(fixture.location)
        Files.writeString(
            fixture.path,
            """
            [player-disconnection]
            policy = "leave_immediately"
            timeout-seconds = 10

            [table]
            break-policy = "allow_waiting_room_only"
            orphaned-policy = "keep_and_warn"

            [mahjong-tile]
            physical-collision-enabled = false
            """.trimIndent(),
        )

        val result = assertIs<MinecraftServerConfigUpdateResult.Success>(fixture.manager.reload())

        assertEquals(DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY, result.config.disconnectedPlayerPolicy)
        assertEquals(false, result.config.mahjongTilePhysicalCollisionEnabled)
        assertEquals(result.config, fixture.state.current)
        assertTrue(fixture.manager.formattedCurrentToml().contains("policy = \"leave_immediately\""))
        assertTrue(fixture.manager.formattedCurrentToml().contains("physical-collision-enabled = false"))
        assertFalse(fixture.manager.formattedCurrentToml().contains("#"))
    }

    /** 損壞 TOML reload 應保留最後一份有效設定且不得覆寫檔案。 */
    @Test
    fun `test failed reload preserves config and file`() = withFixture { fixture ->
        fixture.manager.attach(fixture.location)
        val previous = MinecraftServerConfig(disconnectedPlayerPolicy = DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY)
        fixture.state.replace(previous)
        val invalidContent = "[table]\nbreak-policy = \"not-a-policy\""
        Files.writeString(fixture.path, invalidContent)

        val result = assertIs<MinecraftServerConfigUpdateResult.Failure>(fixture.manager.reload())

        assertTrue(result.message.contains("table.break-policy"))
        assertEquals(previous, fixture.state.current)
        assertEquals(invalidContent, Files.readString(fixture.path))
    }

    /** 啟動時既有檔案損壞應使用程式預設值且不得覆寫檔案。 */
    @Test
    fun `test initialize keeps defaults when existing file is invalid`() = withFixture { fixture ->
        Files.createDirectories(fixture.path.parent)
        val invalidContent = "[player-disconnection]\npolicy = \"unknown\""
        Files.writeString(fixture.path, invalidContent)

        assertIs<MinecraftServerConfigUpdateResult.Failure>(fixture.manager.attach(fixture.location))

        assertEquals(MinecraftServerConfig(), fixture.state.current)
        assertEquals(invalidContent, Files.readString(fixture.path))
    }

    /** 切換 server session 時應先清除上一個世界的有效設定。 */
    @Test
    fun `test attaching another session does not retain previous config`() = withFixture { fixture ->
        fixture.manager.attach(fixture.location)
        fixture.state.replace(MinecraftServerConfig(disconnectedPlayerPolicy = DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY))
        val otherPath = fixture.path.parent.resolve("other.toml")
        Files.writeString(otherPath, "[player-disconnection]\npolicy = \"unknown\"")

        assertIs<MinecraftServerConfigUpdateResult.Failure>(
            fixture.manager.attach(FabricServerConfigLocation(otherPath, "<world save>/serverconfig/other.toml")),
        )

        assertEquals(MinecraftServerConfig(), fixture.state.current)
    }

    /** detach 後應清除位置與有效設定，避免切換世界時沿用 session 資料。 */
    @Test
    fun `test detach clears location and effective config`() = withFixture { fixture ->
        fixture.manager.attach(fixture.location)
        fixture.state.replace(MinecraftServerConfig(disconnectedPlayerPolicy = DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY))

        fixture.manager.detach()

        assertEquals(MinecraftServerConfig(), fixture.state.current)
        assertFailsWith<IllegalStateException> { fixture.manager.path }
    }

    /** 建立使用獨立暫存路徑的 manager 並於測試後移除。 */
    private fun withFixture(block: (Fixture) -> Unit) {
        val directory = createTempDirectory("mahjongcraft-server-config-test")
        try {
            val path = directory.resolve("mahjongcraft/server.toml")
            val location = FabricServerConfigLocation(path, "<test>/server.toml")
            val state = MinecraftServerConfigState()
            block(
                Fixture(
                    path = path,
                    location = location,
                    state = state,
                    manager = FabricServerConfigManager(
                        state,
                        MinecraftServerConfigTomlCodec(),
                        UnusedPathProvider,
                    ),
                ),
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    /**
     * 測試使用的檔案、state 與 manager。
     *
     * @property path 測試設定檔路徑。
     * @property location 測試設定檔位置。
     * @property state 目前有效設定。
     * @property manager 受測檔案 manager。
     */
    private data class Fixture(
        val path: Path,
        val location: FabricServerConfigLocation,
        val state: MinecraftServerConfigState,
        val manager: FabricServerConfigManager,
    )

    /** manager 檔案測試不經由 Minecraft server 解析路徑。 */
    private object UnusedPathProvider : FabricServerConfigPathProvider {
        /** 若測試誤用 server attach，立即回報測試設定錯誤。 */
        override fun get(server: MinecraftServer): FabricServerConfigLocation = error("Unexpected server path lookup")
    }
}
