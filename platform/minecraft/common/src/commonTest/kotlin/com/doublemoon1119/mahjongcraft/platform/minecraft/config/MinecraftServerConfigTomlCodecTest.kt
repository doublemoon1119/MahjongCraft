package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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

            [history]
            enabled = false
            include-ai-matches = false
            include-interrupted-matches = true
            query-enabled = false
            allow-admin-query = false
            max-matches = 250
            retention-days = 120
            max-disk-mib = 512
            """.trimIndent(),
        )

        assertEquals(DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT, config.disconnectedPlayerPolicy)
        assertEquals(45, config.disconnectedPlayerTimeoutSeconds)
        assertEquals(TableBreakPolicy.ALLOW_WAITING_ROOM_ONLY, config.tableBreakPolicy)
        assertEquals(OrphanedTablePolicy.KEEP_AND_WARN, config.orphanedTablePolicy)
        assertEquals(false, config.mahjongTilePhysicalCollisionEnabled)
        assertEquals(false, config.history.enabled)
        assertEquals(false, config.history.includeAiMatches)
        assertEquals(true, config.history.includeInterruptedMatches)
        assertEquals(false, config.history.queryEnabled)
        assertEquals(false, config.history.allowAdminQuery)
        assertEquals(250, config.history.maxMatches)
        assertEquals(120, config.history.retentionDays)
        assertEquals(512, config.history.maxDiskMiB)
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
        assertEquals(MinecraftHistoryConfig(), config.history)
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

    /** 歷史資料整數欄位超出範圍時應拒絕設定。 */
    @Test
    fun `test history integer ranges fail validation`() {
        val invalidValues = listOf(
            "max-matches = -1",
            "retention-days = -1",
            "max-disk-mib = 0",
            "max-disk-mib = 9223372036854775807",
        )

        invalidValues.forEach { value ->
            val exception = assertFailsWith<InvalidMinecraftServerConfigException> {
                codec.decode("[history]\n$value")
            }
            assertTrue(exception.message.orEmpty().contains("history"))
        }
    }

    /** 歷史資料保留天數應轉換為對應的 Kotlin duration。 */
    @Test
    fun `test history retention converts days to duration`() {
        val config = MinecraftHistoryConfig(retentionDays = 3)

        assertEquals(3.days, config.retentionDuration)
        assertEquals(256L * MinecraftHistoryConfig.BYTES_PER_MIB, config.maxDiskBytes)
    }

    /** 零值應代表牌局數量與保留天數不限制。 */
    @Test
    fun `test history zero limits are accepted`() {
        val config = MinecraftHistoryConfig(maxMatches = 0, retentionDays = 0)

        assertEquals(0, config.maxMatches)
        assertEquals(0, config.retentionDays)
        assertEquals(Duration.ZERO, config.retentionDuration)
    }

    /** TOML 錯誤型別與未知歷史設定欄位均不得靜默忽略。 */
    @Test
    fun `test history types and unknown keys are rejected`() {
        listOf(
            "enabled = \"true\"",
            "include-ai-matches = 1",
            "query-enabled = 1",
            "allow-admin-query = 1",
            "max-matches = 1.5",
            "unknown = true",
            "max-matches = 2147483648",
            "retention-days = 2147483648",
        ).forEach { entry ->
            assertFailsWith<InvalidMinecraftServerConfigException>("Invalid history entry must be rejected: $entry") {
                codec.decode("[history]\n$entry")
            }
        }
    }

    /** 可安全換算的最大設定值不應被任意上限拒絕。 */
    @Test
    fun `test largest safe history limits remain finite`() {
        val config = MinecraftHistoryConfig(
            maxMatches = Int.MAX_VALUE,
            retentionDays = Int.MAX_VALUE,
            maxDiskMiB = MinecraftHistoryConfig.MAX_MAX_DISK_MIB,
        )
        assertTrue(config.retentionDuration.isFinite(), "Maximum retention must remain finite")
        assertTrue(config.maxDiskBytes > 0L, "Maximum disk byte count must not overflow")
        assertEquals(config, codec.decode(codec.encode(MinecraftServerConfig(history = config))).history)
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
