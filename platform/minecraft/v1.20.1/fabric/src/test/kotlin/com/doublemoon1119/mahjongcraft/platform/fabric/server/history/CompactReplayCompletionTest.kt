package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證終局 Replay 的 MatchCompleted、桌況結果與返回房間事件邊界。 */
class CompactReplayCompletionTest {
    /** 從正式終局 Replay 的字典 header 還原原始開局設定。 */
    @Test
    fun `completed replay exposes original rule settings without replaying transactions`() {
        val registries = bundledPersistenceRegistries()
        val document = CompactReplayCodec.encodeCompact(validEvents(), HistoryRecordingPersistenceMapper(registries, Json), registries, Json)
        val settings = CompactReplayCodec.decodeRuleSettings(document, registries, matchId)
        assertEquals(table.config, settings.ruleConfig)
        assertEquals(GameFlowConfig(), settings.flowConfig)
    }

    /** 驗證同一終局交易內的桌況結果可接續下一筆返回房間交易。 */
    @Test
    fun `completion and table change in one transaction are accepted`() {
        encode(validEvents())
    }

    /** 終局沒有改變桌況時，不要求額外的桌況結果。 */
    @Test
    fun `completion without table change is accepted`() {
        val original = validEvents()
        encode(listOf(original[0], original[1], original[3].copy(sequence = 3, transactionFirstSequence = 3)))
    }

    /** 驗證 MatchCompleted 後隔一筆交易的桌況結果會被拒絕。 */
    @Test
    fun `table change in later transaction is rejected`() {
        val events = validEvents().map { event ->
            if (event.fact is HistoryFact.TableChanged) {
                event.copy(sequence = 3, transactionFirstSequence = 3, occurredAtEpochMillis = 300)
            } else if (event.fact is HistoryFact.ReturnedToRoom) {
                event.copy(sequence = 4, transactionFirstSequence = 4, occurredAtEpochMillis = 400)
            } else {
                event
            }
        }
        assertValidatorFailure(events)
    }

    /** 驗證同一終局交易不得附加兩筆桌況結果。 */
    @Test
    fun `duplicate table changes are rejected`() {
        val original = validEvents()
        val events = listOf(
            original[0],
            original[1],
            original[2],
            original[2].copy(sequence = 4),
            original[3].copy(sequence = 5, transactionFirstSequence = 5, occurredAtEpochMillis = 500),
        )
        assertValidatorFailure(events)
    }

    /** 驗證 MatchCompleted 後不得重新開始局或追加其他語意事件。 */
    @Test
    fun `round start after completion is rejected`() {
        val events = validEvents().map { event ->
            when (event.fact) {
                is HistoryFact.TableChanged -> event.copy(fact = HistoryFact.RoundStarted(table), sequence = 3, transactionFirstSequence = 3, occurredAtEpochMillis = 300)
                is HistoryFact.ReturnedToRoom -> event.copy(sequence = 4, transactionFirstSequence = 4, occurredAtEpochMillis = 400)
                else -> event
            }
        }
        assertValidatorFailure(events)
    }

    /** 驗證一場 Replay 只能包含一筆 MatchCompleted。 */
    @Test
    fun `duplicate completion is rejected`() {
        val events = listOf(
            validEvents()[0],
            validEvents()[1],
            completion(sequence = 3, transactionFirstSequence = 2, occurredAtEpochMillis = 200),
            validEvents()[2].copy(sequence = 4),
            validEvents()[3].copy(sequence = 5, transactionFirstSequence = 5, occurredAtEpochMillis = 500),
        )
        assertValidatorFailure(events)
    }

    /**
     * 建立內建 persistence mapper、註冊表與 JSON 後編碼事件。
     *
     * @param events 欲驗證並編碼的完整對局事件。
     */
    private fun encode(events: List<HistoryOutboxEvent>) {
        val registries = bundledPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries, Json)
        CompactReplayCodec.encodeCompact(events, mapper, registries, Json)
    }

    /**
     * 確認失敗來自 Replay domain validator，而非後續編碼器。
     *
     * @param events 違反終局事件邊界的完整事件序列。
     */
    private fun assertValidatorFailure(events: List<HistoryOutboxEvent>) {
        val error = assertFailsWith<IllegalArgumentException> { encode(events) }
        assertTrue(error.message.orEmpty().startsWith("Replay"), "Expected a Replay domain validation failure")
    }

    /** 建立 MatchCompleted、同交易桌況結果及下一交易 ReturnedToRoom 的合法事件。 */
    private fun validEvents(): List<HistoryOutboxEvent> = listOf(
        HistoryOutboxEvent(matchId, table.id, 1, 1, 1, 100, null, HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap())),
        completion(sequence = 2, transactionFirstSequence = 2, occurredAtEpochMillis = 200),
        HistoryOutboxEvent(
            matchId,
            table.id,
            1,
            3,
            2,
            200,
            null,
            HistoryFact.TableChanged(HistoryTableResult.Checkpoint("mahjongcraft:test", table)),
        ),
        HistoryOutboxEvent(matchId, table.id, 1, 4, 4, 400, null, HistoryFact.ReturnedToRoom),
    )

    /** 測試用固定桌況，避免使用隨機完整對局。 */
    private val table: TableState = FakeTableStateFactory.create(
        config = RiichiRuleConfig(),
        players = Wind.entries.map { wind -> FakeMahjongPlayerFactory.create(wind, discardPile = RiichiDiscardPile()) },
    )

    /** 測試用單一場次識別碼。 */
    private val matchId: Uuid = Uuid.random()

    /**
     * 建立固定場次的 MatchCompleted 事件。
     *
     * @param sequence 事件在場次中的序號。
     * @param transactionFirstSequence 事件所屬交易的首個序號。
     * @param occurredAtEpochMillis 交易提交時間。
     * @return 此場次的終局事實事件。
     */
    private fun completion(sequence: Long, transactionFirstSequence: Long, occurredAtEpochMillis: Long): HistoryOutboxEvent = HistoryOutboxEvent(
        matchId = matchId,
        venueId = table.id,
        roundNumber = 1,
        sequence = sequence,
        transactionFirstSequence = transactionFirstSequence,
        occurredAtEpochMillis = occurredAtEpochMillis,
        actorPlayerId = null,
        fact = HistoryFact.MatchCompleted(
            "mahjongcraft:test_complete",
            table.players.associate { it.id to it.score },
        ),
    )
}
