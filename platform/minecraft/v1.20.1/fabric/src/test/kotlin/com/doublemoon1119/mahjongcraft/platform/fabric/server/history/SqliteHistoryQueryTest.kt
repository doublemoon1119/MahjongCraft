package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    /** OWN 查詢必須使用 SQL 參與者條件、套用完整性篩選並產生 keyset 游標。 */
    @Test
    fun `test history query applies ownership integrity and pagination`() {
        val database = openDatabase()
        insertMatch(database.path, "owned", 1L, 20L)
        insertReplay(database.path, "owned", "{}")
        insertParticipant(database.path, "owned", 0, "owner")
        insertMatch(database.path, "other", 1L, 10L)
        insertReplay(database.path, "other", "{}")
        insertParticipant(database.path, "other", 0, "different")

        val first = database.readHistoryQueryPage(
            SqliteHistoryQuery(
                playerId = "owner",
                includeAll = false,
                sortField = SqliteHistorySortField.ENDED_AT,
                sortDirection = SqliteHistorySortDirection.DESC,
                integrityFilter = SqliteHistoryIntegrityFilter.COMPLETE,
                pageSize = 1,
            ),
        )

        assertEquals(listOf("owned"), first.entries.map { it.matchId })
        assertTrue(first.nextCursor == null)
    }

    /** ALL 查詢可安全顯示沒有主表與參與者資料的終端診斷，但不應把它當作無 AI 對局。 */
    @Test
    fun `test administrator query exposes terminal only diagnostic without inventing membership`() {
        val database = openDatabase()
        insertTerminal(database.path, "terminal-only", "table-terminal", 500L, false)

        val all = database.readHistoryQueryPage(
            SqliteHistoryQuery(
                playerId = null,
                includeAll = true,
                sortField = SqliteHistorySortField.ENDED_AT,
                sortDirection = SqliteHistorySortDirection.ASC,
            ),
        )
        assertEquals(listOf("terminal-only"), all.entries.map { it.matchId })
        assertEquals(HistoryStoredMatchState.PARTIAL, all.entries.single().state)
        assertEquals(SqliteHistoryMatchOutcome.INTERRUPTED, all.entries.single().outcome)
        assertTrue(all.entries.single().participants.isEmpty())

        val noAi = database.readHistoryQueryPage(
            SqliteHistoryQuery(
                playerId = null,
                includeAll = true,
                sortField = SqliteHistorySortField.ENDED_AT,
                sortDirection = SqliteHistorySortDirection.ASC,
                aiFilter = SqliteHistoryAiFilter.NO_AI,
            ),
        )
        assertTrue(noAi.entries.isEmpty())
    }

    /** OWN 查詢的 participant EXISTS 條件必須具備玩家索引。 */
    @Test
    fun `test participant ownership index exists`() {
        val database = openDatabase()
        val indexes = mutableSetOf<String>()
        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA index_list('history_participant')").use { result ->
                    while (result.next()) indexes += result.getString("name")
                }
            }
        }
        assertTrue(indexes.any { it.contains("player_id") })
    }

    /** 四種排序均可使用穩定的排序值與對局 ID 接續分頁。 */
    @Test
    fun `test all supported sort fields and directions`() {
        val database = openDatabase()
        listOf("first" to Triple(100L, 1, 1000), "second" to Triple(200L, 2, 900), "third" to Triple(200L, 2, 900)).forEach { (id, values) ->
            val (ended, rank, score) = values
            insertMatch(database.path, id, 1L, ended)
            insertReplay(database.path, id, "{}")
            insertParticipant(database.path, id, 0, "owner")
            sql(
                database.path,
                "INSERT INTO history_result_projection(match_id, duration_millis) VALUES (?, ?)",
                id,
                ended - 1L,
            )
            sql(
                database.path,
                "INSERT INTO history_participant_result(match_id, seat_index, final_score, final_rank) VALUES (?, 0, ?, ?)",
                id,
                score,
                rank,
            )
        }
        insertMatch(database.path, "nulls", 300L, 200L)
        insertReplay(database.path, "nulls", "{}")
        insertParticipant(database.path, "nulls", 0, "owner")
        val fields = listOf(
            SqliteHistorySortField.ENDED_AT,
            SqliteHistorySortField.DURATION,
            SqliteHistorySortField.OWN_RANK,
            SqliteHistorySortField.OWN_SCORE,
        )
        fields.forEach { field ->
            SqliteHistorySortDirection.entries.forEach { direction ->
                val page = database.readHistoryQueryPage(
                    SqliteHistoryQuery(
                        playerId = "owner",
                        includeAll = false,
                        sortField = field,
                        sortDirection = direction,
                        pageSize = 10,
                    ),
                )
                assertEquals(4, page.entries.size)
                if (field != SqliteHistorySortField.ENDED_AT) assertEquals("nulls", page.entries.last().matchId)
                val ascending = when (field) {
                    SqliteHistorySortField.ENDED_AT -> listOf("first", "nulls", "second", "third")
                    SqliteHistorySortField.DURATION -> listOf("first", "second", "third", "nulls")
                    SqliteHistorySortField.OWN_RANK -> listOf("first", "second", "third", "nulls")
                    SqliteHistorySortField.OWN_SCORE -> listOf("second", "third", "first", "nulls")
                }
                val descending = when (field) {
                    SqliteHistorySortField.ENDED_AT -> listOf("nulls", "second", "third", "first")
                    SqliteHistorySortField.DURATION -> listOf("second", "third", "first", "nulls")
                    SqliteHistorySortField.OWN_RANK -> listOf("second", "third", "first", "nulls")
                    SqliteHistorySortField.OWN_SCORE -> listOf("first", "second", "third", "nulls")
                }
                val expected = if (direction == SqliteHistorySortDirection.ASC) ascending else descending
                assertEquals(expected, page.entries.map { it.matchId })
                val paged = mutableListOf<String>()
                var cursor: SqliteHistoryCursor? = null
                do {
                    val next = database.readHistoryQueryPage(
                        SqliteHistoryQuery("owner", false, field, direction, pageSize = 1, cursor = cursor),
                    )
                    paged += next.entries.map { it.matchId }
                    cursor = next.nextCursor
                    assertTrue(paged.size <= expected.size, "Keyset pagination must not repeat rows")
                } while (cursor != null)
                assertEquals(expected, paged)
            }
        }

        val first = database.readHistoryQueryPage(
            SqliteHistoryQuery(
                playerId = "owner",
                includeAll = false,
                sortField = SqliteHistorySortField.ENDED_AT,
                sortDirection = SqliteHistorySortDirection.ASC,
                pageSize = 1,
            ),
        )
        deleteMatch(database.path, "first")
        val continuation = database.readHistoryQueryPage(
            SqliteHistoryQuery(
                playerId = "owner",
                includeAll = false,
                sortField = SqliteHistorySortField.ENDED_AT,
                sortDirection = SqliteHistorySortDirection.ASC,
                pageSize = 10,
                cursor = first.nextCursor,
            ),
        )
        assertEquals(listOf("nulls", "second", "third"), continuation.entries.map { it.matchId })
    }

    /** 查詢必須套用規則、終局、AI、完整性、時間與名次篩選。 */
    @Test
    fun `test history query filters are applied without exposing gaps or stops`() {
        val database = openDatabase()
        insertMatch(database.path, "normal", 10L, 100L, ruleId = "rule-a")
        insertReplay(database.path, "normal", "{}")
        insertParticipant(database.path, "normal", 0, "owner")
        insertMatch(database.path, "ai", 10L, 120L, ruleId = "rule-a")
        insertReplay(database.path, "ai", "{}")
        insertParticipant(database.path, "ai", 0, "owner", aiStrategyId = "easy")
        insertMatch(database.path, "interrupted", 10L, 140L, ruleId = "rule-b")
        insertTerminal(database.path, "interrupted", "table-interrupted", 140L, false)
        insertParticipant(database.path, "interrupted", 0, "owner")
        insertMatch(database.path, "gap-only", 10L, 160L, ruleId = "rule-a")
        insertGap(database.path, "gap-only")
        insertMatch(database.path, "stopped-only", 10L, 180L, ruleId = "rule-a")
        insertRecordingStop(database.path, "stopped-only")
        insertMatch(database.path, "tombstoned", 10L, 200L, ruleId = "rule-a")
        insertReplay(database.path, "tombstoned", "{}")
        insertParticipant(database.path, "tombstoned", 0, "owner")
        insertTombstone(database.path, "tombstoned")
        listOf("normal" to 1, "ai" to 2).forEach { (id, rank) ->
            sql(database.path, "INSERT INTO history_participant_result(match_id, seat_index, final_score, final_rank) VALUES (?, 0, ?, ?)", id, 1000, rank)
        }

        val query = { base: SqliteHistoryQuery -> database.readHistoryQueryPage(base).entries.map { it.matchId } }
        val own = SqliteHistoryQuery("owner", false, SqliteHistorySortField.ENDED_AT, SqliteHistorySortDirection.ASC, pageSize = 20)
        assertEquals(listOf("normal", "ai", "interrupted"), query(own))
        assertEquals(listOf("normal", "ai"), query(own.copy(ruleId = "rule-a", integrityFilter = SqliteHistoryIntegrityFilter.COMPLETE)))
        assertEquals(listOf("interrupted"), query(own.copy(outcome = SqliteHistoryOutcomeFilter.INTERRUPTED)))
        assertEquals(listOf("ai"), query(own.copy(aiFilter = SqliteHistoryAiFilter.CONTAINS_AI)))
        assertEquals(listOf("normal"), query(own.copy(aiFilter = SqliteHistoryAiFilter.NO_AI)))
        assertEquals(listOf("normal", "ai"), query(own.copy(endedAtLowerInclusive = 100L, endedAtUpperExclusive = 140L)))
        assertEquals(listOf("normal", "ai"), query(own.copy(minimumRank = 1, maximumRank = 2)))
        assertTrue(query(own.copy(endedAtLowerInclusive = 140L)).contains("interrupted"))
        assertTrue(query(own.copy(endedAtUpperExclusive = 140L).copy(outcome = SqliteHistoryOutcomeFilter.INTERRUPTED)).isEmpty())
        assertTrue(query(own.copy(excludedMatchIds = setOf("normal"))).none { it == "normal" })
    }

    /** 缺少終局證據的缺口與停止記錄不得被管理員查詢公開。 */
    @Test
    fun `test administrator query excludes gap and recording stop without terminal`() {
        val database = openDatabase()
        insertMatch(database.path, "gap", 1L, 2L)
        insertGap(database.path, "gap")
        insertMatch(database.path, "stop", 1L, 2L)
        insertRecordingStop(database.path, "stop")

        val page = database.readHistoryQueryPage(
            SqliteHistoryQuery(null, true, SqliteHistorySortField.ENDED_AT, SqliteHistorySortDirection.ASC),
        )
        assertTrue(page.entries.isEmpty())
    }

    /** participant(player_id, match_id) 索引應被 OWN 查詢的 EXISTS 條件選用。 */
    @Test
    fun `test explain query plan uses participant ownership index`() {
        val database = openDatabase()
        val plan = mutableListOf<String>()
        DriverManager.getConnection("jdbc:sqlite:${database.path}").use { connection ->
            connection.prepareStatement("EXPLAIN QUERY PLAN SELECT 1 FROM history_participant WHERE player_id = ? AND match_id = ?").use { statement ->
                statement.setString(1, "owner")
                statement.setString(2, "match")
                statement.executeQuery().use { result ->
                    while (result.next()) plan += result.getString("detail")
                }
            }
        }
        assertTrue(plan.any { it.contains("player_id") && it.contains("match_id") })
    }

    /** 移除任一必要投影表時，資料庫開啟應明確拒絕且不自動補建。 */
    @Test
    fun `test database rejects missing required projection table`() {
        val path = createTempDirectory("mahjongcraft-history-missing-projection-").resolve("history.sqlite")
        SqliteHistoryDatabase.open(path)
        insertMatch(path, "preserved", 1L, 2L)
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { it.executeUpdate("DROP TABLE history_result_projection") }
        }
        val failure = assertFailsWith<IllegalStateException> { SqliteHistoryDatabase.open(path) }
        assertTrue(failure.message.orEmpty().contains("history_result_projection"))
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT match_id FROM history_match").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("preserved", rows.getString(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'history_result_projection'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
            }
        }
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
     * @param ruleId 規則 ID。
     */
    private fun insertMatch(path: Path, id: String, started: Long, ended: Long, ruleId: String = "rule-$id") = sql(
        path,
        "INSERT INTO history_match(match_id, table_id, rule_id, dimension_id, status, started_at_epoch_millis, ended_at_epoch_millis) VALUES (?, ?, ?, NULL, 'COMPLETED', ?, ?)",
        id,
        "table-$id",
        ruleId,
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
     * @param playerId 玩家 UUID 字串。
     * @param aiStrategyId AI 策略 ID；真人玩家為 null。
     */
    private fun insertParticipant(
        path: Path,
        id: String,
        seat: Int,
        playerId: String = "player",
        aiStrategyId: String? = null,
    ) = sql(
        path,
        "INSERT INTO history_participant(match_id, seat_index, player_id, ai_strategy_id) VALUES (?, ?, ?, ?)",
        id,
        seat,
        playerId,
        aiStrategyId,
    )

    /** 寫入記錄停止診斷列，不代表對局已經安全終止。
     *
     * @param path 資料庫檔案位置。
     * @param id 對局 ID。
     */
    private fun insertRecordingStop(path: Path, id: String) = sql(
        path,
        "INSERT INTO history_recording_stop(match_id, reason) VALUES (?, 'test')",
        id,
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
