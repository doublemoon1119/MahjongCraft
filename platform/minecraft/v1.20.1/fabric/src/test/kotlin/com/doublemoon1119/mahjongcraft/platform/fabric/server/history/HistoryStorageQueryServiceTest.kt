package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** 驗證歷史存檔統計整合 SQL 證據、權威記錄證據與活動對局。 */
class HistoryStorageQueryServiceTest {
    /** 活動、完整、部分及未知場次必須互斥，且未開始記錄的活動對局不得計入。 */
    @Test
    fun `test storage snapshot partitions recorded evidence and protects active games`() = runBlocking {
        val activeMatchId = Uuid.random()
        val unrecordedGame = Game(FakeTableStateFactory.create(), GameFlowConfig())
        val activeGame = Game(
            tableState = FakeTableStateFactory.create(),
            flowConfig = GameFlowConfig(),
            matchId = activeMatchId,
        )
        val completedMatchId = Uuid.random().toString()
        val partialMatchId = Uuid.random().toString()
        val unknownMatchId = Uuid.random().toString()
        val database = openDatabase()
        insertMatch(database.path, activeMatchId.toString(), 10L, 20L)
        insertReplay(database.path, activeMatchId.toString())
        insertMatch(database.path, completedMatchId, 30L, 40L)
        insertReplay(database.path, completedMatchId)
        insertTerminal(database.path, partialMatchId, completed = false)
        insertParticipant(database.path, unknownMatchId)
        insertPending(database.path, activeMatchId.toString())

        val outboxEvent = HistoryOutboxEvent(
            matchId = activeMatchId,
            venueId = activeGame.id,
            roundNumber = activeGame.tableState.roundNumber,
            sequence = 1L,
            occurredAtEpochMillis = 100L,
            actorPlayerId = null,
            fact = HistoryFact.MatchStarted(activeGame.tableState, activeGame.flowConfig),
        )
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(activeGame.id to activeGame, unrecordedGame.id to unrecordedGame),
                historyRecordingState = HistoryRecordingState(
                    nextSequenceByMatchId = mapOf(activeMatchId to 2L),
                    pendingEvents = listOf(outboxEvent),
                ),
            ),
        )
        val policy = HistoryRetentionPolicy(
            includeInterruptedMatches = true,
            maxMatches = 7,
            retentionDuration = Duration.parse("5d"),
            maxDiskBytes = 123_456L,
        )

        val snapshot = HistoryStorageQueryService(store, fixedClock()).read(database, policy)

        assertEquals(1L, snapshot.activeMatchCount)
        assertEquals(1L, snapshot.completedMatchCount)
        assertEquals(1L, snapshot.partialMatchCount)
        assertEquals(1L, snapshot.unknownMatchCount)
        assertEquals(1L, snapshot.pendingSqlEventCount)
        assertEquals(1L, snapshot.pendingOutboxEventCount)
        assertEquals(policy, snapshot.policy)
        assertEquals(Instant.fromEpochMilliseconds(9_876L), snapshot.updatedAt)
        assertSame(policy, snapshot.policy)
    }

    /**
     * 建立每項測試專用的暫存歷史資料庫。
     *
     * @return 已建立並初始化的 SQLite 歷史資料庫。
     */
    private fun openDatabase(): SqliteHistoryDatabase = SqliteHistoryDatabase.open(createTempDirectory("mahjongcraft-history-storage-").resolve("history.sqlite"))

    /**
     * 固定快照時間，避免測試依賴系統時鐘。
     *
     * @return 回傳固定 UTC 時刻的時鐘。
     */
    private fun fixedClock(): Clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(9_876L)
    }

    /**
     * 寫入完整場次摘要。
     *
     * @param path 歷史資料庫檔案路徑。
     * @param id 場次識別碼。
     * @param started 開始時間的 UTC 毫秒時間戳。
     * @param ended 結束時間的 UTC 毫秒時間戳。
     */
    private fun insertMatch(path: Path, id: String, started: Long, ended: Long) = sql(
        path,
        "INSERT INTO history_match(match_id, table_id, rule_id, dimension_id, status, started_at_epoch_millis, ended_at_epoch_millis) VALUES (?, ?, ?, NULL, 'COMPLETED', ?, ?)",
        id,
        "table-$id",
        "rule-$id",
        started,
        ended,
    )

    /**
     * 寫入完整 Replay。
     *
     * @param path 歷史資料庫檔案路徑。
     * @param id 場次識別碼。
     */
    private fun insertReplay(path: Path, id: String) = sql(
        path,
        "INSERT INTO history_replay(match_id, format_version, created_at_epoch_millis, payload) VALUES (?, 1, 1, '{}')",
        id,
    )

    /**
     * 寫入尚未封存的 SQL 事件。
     *
     * @param path 歷史資料庫檔案路徑。
     * @param id 場次識別碼。
     */
    private fun insertPending(path: Path, id: String) = sql(
        path,
        "INSERT INTO history_pending_event(match_id, sequence, round_number, occurred_at_epoch_millis, payload_version, payload) VALUES (?, 1, 1, 1, 1, '{}')",
        id,
    )

    /**
     * 寫入部分場次的終局證據。
     *
     * @param path 歷史資料庫檔案路徑。
     * @param id 場次識別碼。
     * @param completed 場次離開權威狀態時是否已完成整場對局。
     */
    private fun insertTerminal(path: Path, id: String, completed: Boolean) = sql(
        path,
        "INSERT INTO history_terminal(match_id, table_id, ended_at_epoch_millis, completed) VALUES (?, ?, 50, ?)",
        id,
        "table-$id",
        completed,
    )

    /**
     * 寫入沒有摘要的未知子表證據。
     *
     * @param path 歷史資料庫檔案路徑。
     * @param id 場次識別碼。
     */
    private fun insertParticipant(path: Path, id: String) = sql(
        path,
        "INSERT INTO history_participant(match_id, seat_index, player_id, ai_strategy_id) VALUES (?, 0, 'player', NULL)",
        id,
    )

    /**
     * 執行帶參數的測試 SQL。
     *
     * @param path 歷史資料庫檔案路徑。
     * @param statement 帶有問號參數標記的 SQL 陳述式。
     * @param parameters 按 SQL 參數順序排列的值。
     */
    private fun sql(path: Path, statement: String, vararg parameters: Any?) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.prepareStatement(statement).use { prepared ->
                parameters.forEachIndexed { index, value -> prepared.setObject(index + 1, value) }
                prepared.executeUpdate()
            }
        }
    }
}
