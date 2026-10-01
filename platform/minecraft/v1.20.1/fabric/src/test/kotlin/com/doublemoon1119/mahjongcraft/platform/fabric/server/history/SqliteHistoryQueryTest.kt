package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證歷史資料庫的 SQL 分類、摘要分頁與 payload 大小查詢。 */
class SqliteHistoryQueryTest {
    /** 混合完整、部分、未知、子表孤兒與墓碑資料時分類必須互斥且正確。 */
    @Test
    fun `test statistics classifies mixed stored evidence`() {
        val database = openDatabase()
        insertMatch(database.path, "complete", 10L, 20L)
        insertReplay(database.path, "complete", "{}")
        insertTerminal(database.path, "partial", "table-p", 30L, false)
        insertGap(database.path, "partial")
        insertTerminal(database.path, "terminal-only", "table-t", 40L, true)
        insertParticipant(database.path, "orphan", 0)
        insertGap(database.path, "gap-only")
        insertMatch(database.path, "tombstone", 1L, 2L)
        insertReplay(database.path, "tombstone", "ignored")
        insertTombstone(database.path, "tombstone")

        val statistics = database.readStatistics()

        assertEquals(HistoryStoredMatchState.COMPLETED, statistics.matchStates["complete"])
        assertEquals(HistoryStoredMatchState.PARTIAL, statistics.matchStates["partial"])
        assertEquals(HistoryStoredMatchState.UNKNOWN, statistics.matchStates["terminal-only"])
        assertEquals(HistoryStoredMatchState.UNKNOWN, statistics.matchStates["orphan"])
        assertEquals(HistoryStoredMatchState.UNKNOWN, statistics.matchStates["gap-only"])
        assertNull(statistics.matchStates["tombstone"])
        assertEquals(1L, statistics.tombstoneCount)
    }

    /** Unicode payload 必須依 UTF-8 BLOB 長度計算，且查詢不依賴載入 payload。 */
    @Test
    fun `test logical payload bytes count unicode replay and pending`() {
        val database = openDatabase()
        insertMatch(database.path, "unicode", 1L, 2L)
        insertReplay(database.path, "unicode", "龍a")
        insertPending(database.path, "unicode", "😀")

        assertEquals("龍a😀".toByteArray(Charsets.UTF_8).size.toLong(), database.logicalPayloadBytes(listOf("unicode")))
    }

    /** 分頁必須套用邊界、同時間排序、游標列刪除與排除 ID。 */
    @Test
    fun `test summary pagination bounds ties deleted cursor row and exclusions`() {
        val database = openDatabase()
        listOf("a", "b", "c", "d").forEach { id ->
            insertMatch(database.path, id, 1L, 100L)
            insertReplay(database.path, id, "{}")
        }

        val first = database.readSummaryPage(limit = 2, excludedMatchIds = setOf("b"))
        assertEquals(listOf("a", "c"), first.entries.map { it.matchId })
        assertTrue(first.nextCursor != null)

        deleteMatch(database.path, "c")
        val second = database.readSummaryPage(limit = 2, cursor = first.nextCursor)
        assertEquals(listOf("d"), second.entries.map { it.matchId })
        assertNull(second.nextCursor)
        assertEquals(1, database.readSummaryPage(limit = 0).entries.size)
        assertEquals(3, database.readSummaryPage(limit = 101).entries.size)
    }

    /** 沒有摘要列的部分場次仍須回傳可用終局時間與可為 null 的 metadata。 */
    @Test
    fun `test partial summary allows nullable metadata`() {
        val database = openDatabase()
        insertTerminal(database.path, "partial-only", "table-p", 500L, false)

        val entry = database.readSummaryPage().entries.single()

        assertEquals("partial-only", entry.matchId)
        assertEquals(HistoryStoredMatchState.PARTIAL, entry.state)
        assertEquals(500L, entry.endedAtEpochMillis)
        assertNull(entry.startedAtEpochMillis)
        assertNull(entry.ruleId)
        assertEquals("table-p", entry.tableId)
        assertNull(entry.dimensionId)
    }

    /** 超過一百筆時查詢仍只回傳有界頁面，重複終止證據只佔一個場次。 */
    @Test
    fun `summary page caps at one hundred and deduplicates terminals`() {
        val database = openDatabase()
        database.recordTerminals((1..105).map { index -> HistoryTerminalRecord("match-$index", "table", index.toLong(), false) })
        database.recordTerminals(listOf(HistoryTerminalRecord("match-1", "other-table", 1L, false)))
        val page = database.readSummaryPage(limit = 1000)
        assertEquals(100, page.entries.size)
        assertEquals(100, page.entries.map { it.matchId }.distinct().size)
        assertEquals(5, database.readSummaryPage(cursor = page.nextCursor).entries.size)
        assertEquals(105, database.readStatistics().matchStates.size)
    }

    /** 單引號對局 ID 必須安全支援游標、排除條件與 payload 大小查詢。 */
    @Test
    fun `test quoted match id is escaped in query predicates`() {
        val database = openDatabase()
        val quotedId = "q'id"
        insertMatch(database.path, quotedId, 1L, 2L)
        insertReplay(database.path, quotedId, "é")

        assertEquals(2L, database.logicalPayloadBytes(listOf(quotedId)))
        assertTrue(database.readSummaryPage(excludedMatchIds = setOf("other")).entries.any { it.matchId == quotedId })
        assertTrue(database.readSummaryPage(excludedMatchIds = setOf(quotedId)).entries.none { it.matchId == quotedId })
        assertTrue(database.readSummaryPage(cursor = HistorySummaryCursor(1L, "q'abc")).entries.any { it.matchId == quotedId })
    }

    /**
     * 建立每項測試專用的暫存歷史資料庫。
     *
     * @return 新建立的歷史資料庫。
     */
    private fun openDatabase(): SqliteHistoryDatabase = SqliteHistoryDatabase.open(createTempDirectory("mahjongcraft-history-query-").resolve("history.sqlite"))

    /** 寫入摘要列。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     * @param started 開局時間。
     * @param ended 結束時間。
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

    /** 寫入 Replay 列。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     * @param payload Replay payload。
     */
    private fun insertReplay(path: Path, id: String, payload: String) = sql(
        path,
        "INSERT INTO history_replay(match_id, format_version, created_at_epoch_millis, payload) VALUES (?, 1, 1, ?)",
        id,
        payload,
    )

    /** 寫入暫存事件列。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     * @param payload 事件 payload。
     */
    private fun insertPending(path: Path, id: String, payload: String) = sql(
        path,
        "INSERT INTO history_pending_event(match_id, sequence, round_number, occurred_at_epoch_millis, payload_version, payload) VALUES (?, 1, 1, 1, 1, ?)",
        id,
        payload,
    )

    /** 寫入終局證據列。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     * @param tableId 牌桌 ID。
     * @param ended 終局時間。
     * @param completed 是否由完整終局流程確認。
     */
    private fun insertTerminal(path: Path, id: String, tableId: String, ended: Long, completed: Boolean) = sql(
        path,
        "INSERT INTO history_terminal(match_id, table_id, ended_at_epoch_millis, completed) VALUES (?, ?, ?, ?)",
        id,
        tableId,
        ended,
        completed,
    )

    /** 寫入缺口證據列。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     */
    private fun insertGap(path: Path, id: String) = sql(
        path,
        "INSERT INTO history_gap(match_id, first_missing_sequence) VALUES (?, 2)",
        id,
    )

    /** 寫入子表孤兒列。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     * @param seat 座位索引。
     */
    private fun insertParticipant(path: Path, id: String, seat: Int) = sql(
        path,
        "INSERT INTO history_participant(match_id, seat_index, player_id, ai_strategy_id) VALUES (?, ?, 'player', NULL)",
        id,
        seat,
    )

    /** 寫入清理墓碑列。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     */
    private fun insertTombstone(path: Path, id: String) = sql(
        path,
        "INSERT INTO history_tombstone(match_id, pruned_at_epoch_millis, reason) VALUES (?, 1, 'test')",
        id,
    )

    /** 刪除指定摘要與 Replay，模擬游標列已被清理。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     */
    private fun deleteMatch(path: Path, id: String) {
        sql(path, "DELETE FROM history_replay WHERE match_id = ?", id)
        sql(path, "DELETE FROM history_match WHERE match_id = ?", id)
    }

    /** 執行帶參數的測試 SQL。
     *
     * @param path 資料庫檔案位置。
     * @param statement SQL 語句。
     * @param parameters SQL 綁定參數。
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
