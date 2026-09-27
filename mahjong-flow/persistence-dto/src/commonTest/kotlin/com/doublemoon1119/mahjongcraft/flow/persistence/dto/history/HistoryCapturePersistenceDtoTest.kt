package com.doublemoon1119.mahjongcraft.flow.persistence.dto.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryCaptureState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證歷史 outbox 的持久化 DTO 可保存並還原穩定序號與事實。 */
class HistoryCapturePersistenceDtoTest {
    /** 驗證含待寫事件、下一序號與缺口 checkpoint 的完整 round-trip。 */
    @Test
    fun `history capture state round trips through mapper`() {
        val matchId = Uuid.random()
        val tableId = Uuid.random()
        val state = HistoryCaptureState(
            nextSequenceByMatchId = mapOf(matchId to 4L),
            pendingEvents = listOf(
                HistoryOutboxEvent(
                    matchId = matchId,
                    tableId = tableId,
                    roundNumber = 1,
                    sequence = 3L,
                    occurredAtEpochMillis = 123L,
                    actorPlayerId = null,
                    fact = HistoryFact.ReturnedToRoom,
                ),
            ),
            firstMissingSequenceByMatchId = mapOf(matchId to 2L),
        )
        val mapper = HistoryCapturePersistenceMapper(buildBuiltInPersistenceRegistries())

        assertEquals(state, mapper.decode(mapper.encode(state)))
    }

    /** 單筆事件無法解碼時留下序號缺口，不讓歷史附加資料阻止權威存檔載入。 */
    @Test
    fun `undecodable history event leaves a checkpoint gap`() {
        val matchId = Uuid.random()
        val tableId = Uuid.random()
        val dto = HistoryCapturePersistenceDto(
            nextSequenceByMatchId = mapOf(matchId.toString() to 3L),
            pendingEvents = listOf(
                HistoryOutboxEventPersistenceDto(
                    matchId.toString(),
                    tableId.toString(),
                    1,
                    1L,
                    123L,
                    null,
                    HistoryFactPersistenceDto.MatchCompleted("test", mapOf("invalid-player-id" to 1)),
                ),
                HistoryOutboxEventPersistenceDto(
                    matchId.toString(),
                    tableId.toString(),
                    1,
                    2L,
                    124L,
                    null,
                    HistoryFactPersistenceDto.ReturnedToRoom,
                ),
            ),
        )

        val decoded = HistoryCapturePersistenceMapper(buildBuiltInPersistenceRegistries()).decode(dto)

        assertEquals(listOf(2L), decoded.pendingEvents.map { it.sequence })
        assertEquals(1L, decoded.firstMissingSequenceByMatchId.getValue(matchId))
    }
}
