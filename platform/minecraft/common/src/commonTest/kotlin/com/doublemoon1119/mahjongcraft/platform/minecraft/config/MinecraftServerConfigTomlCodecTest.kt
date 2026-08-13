package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [MinecraftServerConfigTomlCodec] 的嚴格解碼、驗證與標準輸出測試。 */
class MinecraftServerConfigTomlCodecTest {
    /** 測試使用的 codec。 */
    private val codec = MinecraftServerConfigTomlCodec()

    /** 完整 TOML 應映射所有 server policy。 */
    @Test
    fun `test complete toml decodes all policies`() {
        val config = codec.decode(
            """
            [player-disconnection]
            policy = "leave_after_timeout"
            timeout-seconds = 45

            [table]
            break-policy = "allow_waiting_room_only"
            orphaned-policy = "keep_and_warn"

            [mahjong-tile]
            physical-collision-enabled = false
            """.trimIndent(),
        )

        assertEquals(DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT, config.disconnectedPlayerPolicy)
        assertEquals(45, config.disconnectedPlayerTimeoutSeconds)
        assertEquals(TableBreakPolicy.ALLOW_WAITING_ROOM_ONLY, config.tableBreakPolicy)
        assertEquals(OrphanedTablePolicy.KEEP_AND_WARN, config.orphanedTablePolicy)
        assertEquals(false, config.mahjongTilePhysicalCollisionEnabled)
    }

    /** 缺少可選 section 或欄位時應使用程式預設值。 */
    @Test
    fun `test missing optional fields use defaults`() {
        val config = codec.decode(
            """
            [player-disconnection]
            policy = "leave_immediately"
            """.trimIndent(),
        )

        assertEquals(DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY, config.disconnectedPlayerPolicy)
        assertEquals(MinecraftServerConfig.DEFAULT_DISCONNECTED_PLAYER_TIMEOUT_SECONDS, config.disconnectedPlayerTimeoutSeconds)
        assertEquals(TableBreakPolicy.DENY_WHILE_OCCUPIED, config.tableBreakPolicy)
        assertEquals(OrphanedTablePolicy.REMOVE_ALL, config.orphanedTablePolicy)
        assertEquals(true, config.mahjongTilePhysicalCollisionEnabled)
    }

    /** 未知欄位不得被忽略。 */
    @Test
    fun `test unknown field fails decoding`() {
        val exception = assertFailsWith<InvalidMinecraftServerConfigException> {
            codec.decode(
                """
                [table]
                break-policy = "deny_while_occupied"
                misspelled-policy = "remove_all"
                """.trimIndent(),
            )
        }

        assertTrue(exception.message.orEmpty().contains("misspelled-policy"))
    }

    /** 未知 enum 值應列出欄位與允許值。 */
    @Test
    fun `test unsupported enum reports allowed values`() {
        val exception = assertFailsWith<InvalidMinecraftServerConfigException> {
            codec.decode(
                """
                [player-disconnection]
                policy = "eventually"
                """.trimIndent(),
            )
        }

        assertTrue(exception.message.orEmpty().contains("player-disconnection.policy"))
        assertTrue(exception.message.orEmpty().contains("keep_seat"))
        assertTrue(exception.message.orEmpty().contains("leave_after_timeout"))
    }

    /** 斷線逾時超出範圍時應回報合法邊界。 */
    @Test
    fun `test timeout outside range fails validation`() {
        val belowMinimum = assertFailsWith<InvalidMinecraftServerConfigException> {
            codec.decode(
                """
                [player-disconnection]
                timeout-seconds = 0
                """.trimIndent(),
            )
        }
        val aboveMaximum = assertFailsWith<InvalidMinecraftServerConfigException> {
            codec.decode(
                """
                [player-disconnection]
                timeout-seconds = 3601
                """.trimIndent(),
            )
        }

        assertTrue(belowMinimum.message.orEmpty().contains("1 and 3600"))
        assertTrue(aboveMaximum.message.orEmpty().contains("1 and 3600"))
    }

    /** 標準化輸出應可完整 round-trip。 */
    @Test
    fun `test canonical toml round trips config`() {
        val expected = MinecraftServerConfig(
            disconnectedPlayerPolicy = DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT,
            disconnectedPlayerTimeoutSeconds = 90,
            tableBreakPolicy = TableBreakPolicy.ALLOW_AND_TERMINATE,
            orphanedTablePolicy = OrphanedTablePolicy.REMOVE_WAITING_ROOM,
            mahjongTilePhysicalCollisionEnabled = false,
        )

        val encoded = codec.encode(expected)

        assertEquals(expected, codec.decode(encoded))
        assertTrue(encoded.startsWith("[player-disconnection]"))
        assertTrue("#" !in encoded)
    }
}
