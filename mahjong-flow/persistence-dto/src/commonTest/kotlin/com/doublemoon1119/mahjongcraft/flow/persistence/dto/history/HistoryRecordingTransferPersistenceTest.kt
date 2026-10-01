package com.doublemoon1119.mahjongcraft.flow.persistence.dto.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTransfer
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證隔離歷史轉移 metadata 可完整持久化並還原。 */
class HistoryRecordingTransferPersistenceTest {
    /** 轉移牌桌、最近完整批次與完成旗標經 DTO 及 JSON 往返後保持一致。 */
    @Test
    fun `history transfer metadata round trips through mapper and JSON`() {
        val matchId = Uuid.random()
        val tableId = Uuid.random()
        val event = HistoryOutboxEvent(
            matchId = matchId,
            tableId = tableId,
            roundNumber = 1,
            sequence = 1L,
            occurredAtEpochMillis = 123L,
            actorPlayerId = null,
            fact = HistoryFact.ReturnedToRoom,
        )
        val state = HistoryRecordingState(
            nextSequenceByMatchId = mapOf(matchId to 2L),
            transfersByMatchId = mapOf(
                matchId to HistoryRecordingTransfer(
                    tableId = tableId,
                    lastAcceptedBatch = listOf(event),
                    matchCompleted = true,
                ),
            ),
        )
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())

        val dto = mapper.encode(state)
        val encoded = Json.encodeToString(HistoryRecordingPersistenceDto.serializer(), dto)
        val decodedDto = Json.decodeFromString(HistoryRecordingPersistenceDto.serializer(), encoded)

        assertTrue(encoded.contains("transfersByMatchId"))
        assertEquals(state, mapper.decode(decodedDto))
    }
}
