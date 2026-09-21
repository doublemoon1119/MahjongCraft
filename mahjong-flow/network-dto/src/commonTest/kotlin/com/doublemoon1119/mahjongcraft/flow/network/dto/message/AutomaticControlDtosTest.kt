package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 本局自動操作更新與個人快照 DTO 的往返測試。 */
class AutomaticControlDtosTest {
    /** 更新 request 必須保留 request ID、revision 與完整控制集合。 */
    @Test
    fun `automatic control update request round-trips`() {
        val expected = AutomaticControlUpdateRequestDto(
            requestId = "request-1",
            gameId = Uuid.random().toString(),
            expectedRevision = 7L,
            enabledControlIds = setOf("example:auto_one", "example:auto_two"),
        )

        val encoded = Json.encodeToString(AutomaticControlUpdateRequestDto.serializer(), expected)

        assertEquals(expected, Json.decodeFromString(AutomaticControlUpdateRequestDto.serializer(), encoded))
    }

    /** 每種處理結果與可選的最新快照都必須無損通過 JSON。 */
    @Test
    fun `every automatic control update result round-trips`() {
        AutomaticControlUpdateResultKindDto.entries.forEach { result ->
            val expected = AutomaticControlUpdateResultDto(
                requestId = "request-1",
                gameId = "game-1",
                result = result,
                snapshot = AutomaticControlSnapshotDto(
                    gameId = "game-1",
                    revision = 8L,
                    supportedControlIds = setOf("example:auto_one"),
                    enabledControlIds = setOf("example:auto_one"),
                ),
            )
            val encoded = Json.encodeToString(AutomaticControlUpdateResultDto.serializer(), expected)

            assertEquals(expected, Json.decodeFromString(AutomaticControlUpdateResultDto.serializer(), encoded))
        }
    }

    /** 個人權威快照與傳輸 DTO 之間必須保持 UUID、revision 與集合。 */
    @Test
    fun `automatic control snapshot maps between domain and dto`() {
        val expected = PlayerAutomaticControlSnapshot(
            gameId = Uuid.random(),
            revision = 9L,
            supportedControlIds = setOf("example:auto_one", "example:auto_two"),
            enabledControlIds = setOf("example:auto_two"),
        )

        assertEquals(expected, expected.toDto().toDomain())
    }
}
