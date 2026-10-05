package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證歷史格式種類識別碼維持既有的文字契約。 */
class HistoryContractKeysTest {
    /** 歷史事實種類識別碼必須保持既有文字值。 */
    @Test
    fun `history fact type keys preserve serialized contract`() {
        assertEquals(
            listOf(
                "match_started",
                "round_started",
                "round_preparation_started",
                "round_preparation_submitted",
                "round_preparation_automatic_resolved",
                "action_accepted",
                "reaction_resolved",
                "round_completed",
                "win_settled",
                "match_completed",
                "win_continuation_resolved",
                "rule_effect_resolved",
                "table_changed",
                "returned_to_room",
            ),
            listOf(
                HistoryFactPersistenceDto.MatchStarted.serializer(),
                HistoryFactPersistenceDto.RoundStarted.serializer(),
                HistoryFactPersistenceDto.RoundPreparationStarted.serializer(),
                HistoryFactPersistenceDto.RoundPreparationSubmitted.serializer(),
                HistoryFactPersistenceDto.RoundPreparationAutomaticallyResolved.serializer(),
                HistoryFactPersistenceDto.ActionAccepted.serializer(),
                HistoryFactPersistenceDto.ReactionResolved.serializer(),
                HistoryFactPersistenceDto.RoundCompleted.serializer(),
                HistoryFactPersistenceDto.WinSettled.serializer(),
                HistoryFactPersistenceDto.MatchCompleted.serializer(),
                HistoryFactPersistenceDto.WinContinuationResolved.serializer(),
                HistoryFactPersistenceDto.RuleEffectResolved.serializer(),
                HistoryFactPersistenceDto.TableChanged.serializer(),
                HistoryFactPersistenceDto.ReturnedToRoom.serializer(),
            ).map { it.descriptor.serialName },
        )
    }

    /** 和牌明細值種類識別碼必須保持既有文字值。 */
    @Test
    fun `detail value type keys preserve serialized contract`() {
        assertEquals("quantities", HistoryDetailValueTypeKeys.QUANTITIES)
        assertEquals("tiles", HistoryDetailValueTypeKeys.TILES)
        assertEquals("entries", HistoryDetailValueTypeKeys.ENTRIES)

        assertEquals(
            listOf("quantities", "tiles", "entries"),
            listOf(
                HistoryWinDetailValuePersistenceDto.Quantities.serializer(),
                HistoryWinDetailValuePersistenceDto.Tiles.serializer(),
                HistoryWinDetailValuePersistenceDto.Entries.serializer(),
            ).map { it.descriptor.serialName },
        )

        val value = HistoryWinDetailValuePersistenceDto.Quantities(listOf(HistoryWinDetailQuantityPersistenceDto("test:unit", 1)))
        val encoded = Json.encodeToString(HistoryWinDetailValuePersistenceDto.serializer(), value)
        assertEquals("quantities", Json.parseToJsonElement(encoded).jsonObject.getValue("type").jsonPrimitive.content)
    }
}
