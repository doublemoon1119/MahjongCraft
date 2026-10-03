package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPlayerChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證歷史 outbox 的持久化 DTO 可保存並還原穩定序號與事實。 */
class HistoryRecordingPersistenceDtoTest {
    /** 終局 metadata 經持久化 DTO 往返後保持完整。 */
    @Test
    fun `terminal metadata round trips`() {
        val matchId = Uuid.random()
        val terminal = HistoryRecordingTerminal(42L, true, Uuid.random())
        val state = HistoryRecordingState(terminalByMatchId = mapOf(matchId to terminal))
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())
        assertEquals(state, mapper.decode(mapper.encode(state)))
    }

    /** 終局 metadata 的牌桌 UUID 無法解析時必須拒絕資料。 */
    @Test
    fun `malformed terminal table ID fails decoding`() {
        val dto = HistoryRecordingPersistenceDto(
            terminalByMatchId = mapOf(
                Uuid.random().toString() to HistoryRecordingTerminalPersistenceDto(1L, false, "not-a-uuid"),
            ),
        )
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())
        assertFailsWith<IllegalArgumentException> { mapper.decode(dto) }
    }

    /** 交易結果的局部差異在 JSON 與 DTO 往返後仍能重建玩家分數。 */
    @Test
    fun `table change round trips without full table snapshot`() {
        val player = MahjongPlayer(Uuid.random(), 0, discardPile = TaiwanDiscardPile(), seatWind = Wind.EAST)
        val matchId = Uuid.random()
        val change = HistoryTableChange(
            changedPlayers = listOf(HistoryPlayerChange(player.copy(score = 100), 0, listOf(GameAction.Draw))),
        )
        val state = HistoryRecordingState(
            nextSequenceByMatchId = mapOf(matchId to 2L),
            pendingEvents = listOf(
                HistoryOutboxEvent(
                    matchId = matchId,
                    tableId = Uuid.random(),
                    roundNumber = 1,
                    sequence = 1L,
                    occurredAtEpochMillis = 1L,
                    actorPlayerId = null,
                    fact = HistoryFact.TableChanged(HistoryTableResult.Change(change)),
                ),
            ),
        )
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())
        val dto = mapper.encode(state)
        val encoded = Json.encodeToString(HistoryRecordingPersistenceDto.serializer(), dto)

        assertTrue(!encoded.contains("resultingState"))
        assertEquals(state, mapper.decode(Json.decodeFromString(HistoryRecordingPersistenceDto.serializer(), encoded)))
    }

    /** 即使版本為預設值，也必須明確輸出歷史格式版本。 */
    @Test
    fun `history format version is always serialized`() {
        val encoded = Json.encodeToString(HistoryRecordingPersistenceDto.serializer(), HistoryRecordingPersistenceDto())
        assertTrue(encoded.contains("\"formatVersion\":1"))
    }

    /** 驗證含待寫事件、下一序號與缺口 checkpoint 的完整 round-trip。 */
    @Test
    fun `history recording state round trips through mapper`() {
        val matchId = Uuid.random()
        val tableId = Uuid.random()
        val state = HistoryRecordingState(
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
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())

        assertEquals(state, mapper.decode(mapper.encode(state)))
    }

    /** 驗證所有歷史記錄決策均以穩定列舉名稱往返持久化。 */
    @Test
    fun `all history recording decisions round trip through mapper`() {
        val decisionsByMatchId = HistoryRecordingDecision.entries.associate { decision ->
            Uuid.random() to decision
        }
        val state = HistoryRecordingState(decisionsByMatchId = decisionsByMatchId)
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())

        assertEquals(state, mapper.decode(mapper.encode(state)))
    }

    /** 未知的歷史記錄決策名稱必須拒絕載入，避免錯誤地視為可記錄。 */
    @Test
    fun `unknown history recording decision rejects decoding`() {
        val matchId = Uuid.random()
        val dto = HistoryRecordingPersistenceDto(
            decisionsByMatchId = mapOf(matchId.toString() to "UNKNOWN_DECISION"),
        )

        assertFailsWith<IllegalArgumentException> {
            HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries()).decode(dto)
        }
    }

    /** 單筆事件無法解碼時留下序號缺口，不讓歷史附加資料阻止權威存檔載入。 */
    @Test
    fun `undecodable history event leaves a checkpoint gap`() {
        val matchId = Uuid.random()
        val tableId = Uuid.random()
        val dto = HistoryRecordingPersistenceDto(
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

        val decoded = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries()).decode(dto)

        assertEquals(listOf(2L), decoded.pendingEvents.map { it.sequence })
        assertEquals(1L, decoded.firstMissingSequenceByMatchId.getValue(matchId))
    }

    /** 胡牌詳情的文字、條目與牌參照均可完整往返。 */
    @Test
    fun `win settled details round trip`() {
        val matchId = Uuid.random()
        val winner = Uuid.random()
        val tile = Uuid.random()
        val details = HistoryWinDetails(
            winner,
            "mahjongcraft:riichi",
            listOf(
                WinSettlementDetailField("mahjongcraft:text", WinSettlementDetailValue.Text("mahjongcraft.han", listOf("3"))),
                WinSettlementDetailField(
                    "mahjongcraft:entries",
                    WinSettlementDetailValue.Entries(listOf(WinSettlementDetailValue.Entries.Entry("mahjongcraft.yaku", trailingText = "1"))),
                ),
                WinSettlementDetailField("mahjongcraft:tiles", WinSettlementDetailValue.Tiles(listOf(tile))),
            ),
        )
        val state = HistoryRecordingState(
            pendingEvents = listOf(
                HistoryOutboxEvent(
                    matchId,
                    Uuid.random(),
                    1,
                    1L,
                    occurredAtEpochMillis = 1L,
                    actorPlayerId = winner,
                    fact = HistoryFact.WinSettled("mahjongcraft:ron", listOf(details), listOf(Uuid.random())),
                ),
            ),
        )
        val mapper = HistoryRecordingPersistenceMapper(buildBuiltInPersistenceRegistries())
        assertEquals(state, mapper.decode(mapper.encode(state)))
    }
}
