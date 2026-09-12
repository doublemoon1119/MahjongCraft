package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** 玩家決策提交與 ACK 線路格式的往返測試。 */
class PlayerDecisionSubmissionDtoTest {
    /** 最終選牌提交必須保留 submission ID 與牌張順序。 */
    @Test
    fun `test final selection round-trips with submission id`() {
        val expected = PlayerDecisionSelectionDto(
            gameId = "game",
            decisionKey = "decision",
            kind = PlayerDecisionSelectionKindDto.ACTION,
            token = "action",
            tileIds = listOf("tile-1", "tile-2"),
            submissionId = "submission",
        )

        val encoded = Json.encodeToString(PlayerDecisionSelectionDto.serializer(), expected)

        assertEquals(expected, Json.decodeFromString(PlayerDecisionSelectionDto.serializer(), encoded))
    }

    /** 三種 ACK 結果都必須能無損通過 JSON。 */
    @Test
    fun `test every submission result round-trips`() {
        PlayerDecisionSubmissionResultKindDto.entries.forEach { result ->
            val expected = PlayerDecisionSubmissionResultDto("game", "decision", "submission", result)
            val encoded = Json.encodeToString(PlayerDecisionSubmissionResultDto.serializer(), expected)

            assertEquals(expected, Json.decodeFromString(PlayerDecisionSubmissionResultDto.serializer(), encoded))
        }
    }

    /** 開始多選仍是沒有 submission ID 的呈現意圖。 */
    @Test
    fun `test begin tile selection does not require submission id`() {
        val expected = PlayerDecisionSelectionDto(
            gameId = "game",
            decisionKey = "decision",
            kind = PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION,
        )

        val encoded = Json.encodeToString(PlayerDecisionSelectionDto.serializer(), expected)

        assertEquals(expected, Json.decodeFromString(PlayerDecisionSelectionDto.serializer(), encoded))
    }
}
