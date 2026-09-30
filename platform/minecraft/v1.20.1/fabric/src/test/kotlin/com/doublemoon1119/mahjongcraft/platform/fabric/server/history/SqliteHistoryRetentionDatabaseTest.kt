package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SQLite 歷史保留資料邊界測試。 */
class SqliteHistoryRetentionDatabaseTest {
    /** 清理、墓碑與重新開庫後的延遲寫入皆保持冪等。 */
    @Test
    fun `test pruning persists tombstone and rejects late writes`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val matchId = "00000000-0000-0000-0000-000000000101"
        database.appendPending(pending(matchId))
        database.recordGaps(mapOf(matchId to 2L))
        database.recordTerminals(listOf(HistoryTerminalRecord(matchId, "table", 500L, false)))

        assertEquals(1, database.pruneMatches(mapOf(matchId to "EXPIRED"), 500L))
        assertEquals(0, database.pruneMatches(mapOf(matchId to "EXPIRED"), 501L))
        val reopened = SqliteHistoryDatabase.open(path)
        assertEquals(setOf(matchId), reopened.readTombstones())
        reopened.appendPending(pending(matchId, sequence = 2L))
        reopened.recordGaps(mapOf(matchId to 3L))
        reopened.recordRecordingStops(mapOf(matchId to "LATE"))
        reopened.recordTerminals(listOf(HistoryTerminalRecord(matchId, "table", 600L, false)))
        assertTrue(reopened.readPending(matchId).isEmpty())
        assertTrue(reopened.readRecordingStops().isEmpty())
        assertTrue(reopened.readRetentionCandidates().isEmpty())
    }

    /** 部分候選必須有終端證據，缺口或停止診斷單獨存在時不得產生候選。 */
    @Test
    fun `test retention candidates require persisted terminal`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val gapOnly = "00000000-0000-0000-0000-000000000102"
        val partial = "00000000-0000-0000-0000-000000000103"
        database.appendPending(pending(gapOnly))
        database.recordGaps(mapOf(gapOnly to 2L))
        database.appendPending(pending(partial))
        database.recordGaps(mapOf(partial to 2L))
        database.recordTerminals(listOf(HistoryTerminalRecord(partial, "table", 700L, false)))

        assertEquals(listOf(partial), database.readRetentionCandidates().map { it.matchId })
        assertTrue(database.readRetentionCandidates().single().interrupted)
    }

    /** 墓碑 ID 格式錯誤時，清理確認不可靜默略過。 */
    @Test
    fun `test malformed tombstone proof fails`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val matchId = "00000000-0000-0000-0000-000000000104"
        database.recordTerminals(listOf(HistoryTerminalRecord(matchId, "table", 800L, false)))
        database.pruneMatches(mapOf(matchId to "EXPIRED"), 800L)
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE history_tombstone SET match_id = 'malformed'")
            }
        }
        assertFailsWith<IllegalStateException> { database.readPruningConfirmations() }
    }

    /** 不存在的 SQLite sidecar 必須計為零，且總和準確。 */
    @Test
    fun `test disk usage treats missing sidecars as zero`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val usage = database.measureDiskUsage()
        assertTrue(usage.dbBytes > 0L)
        assertEquals(usage.dbBytes + usage.walBytes + usage.shmBytes, usage.totalBytes)
    }

    /** 新資料庫啟用 incremental vacuum，重新開啟不得改變既有模式。 */
    @Test
    fun `test fresh database enables incremental vacuum and reopen preserves mode`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        SqliteHistoryDatabase.open(path)
        fun vacuumMode(): Int = DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA auto_vacuum").use { rows ->
                    assertTrue(rows.next())
                    rows.getInt(1)
                }
            }
        }
        assertEquals(2, vacuumMode())
        SqliteHistoryDatabase.open(path)
        assertEquals(2, vacuumMode())
    }

    /** 讀取連線存在時，回收操作應以 busy 結果安全返回而不破壞資料庫。 */
    @Test
    fun `test recovery remains safe while reader is open`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        DriverManager.getConnection("jdbc:sqlite:$path").use { reader ->
            reader.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
            reader.autoCommit = false
            reader.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM history_schema_version").use { rows -> assertTrue(rows.next()) }
                database.appendPending(pending("00000000-0000-0000-0000-000000000110"))
                val recovery = database.recoverDiskSpace()
                assertTrue(recovery.busy)
                assertTrue(recovery.incrementalSupported)
            }
            reader.rollback()
        }
        assertTrue(database.measureDiskUsage().dbBytes > 0L)
    }

    /** 清理數 MB 暫存事件後，重複有界回收應降低實際檔案大小。 */
    @Test
    fun `test bounded recovery reclaims deleted payload`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val matchId = "00000000-0000-0000-0000-000000000107"
        database.appendPending(pending(matchId).copy(payload = "x".repeat(3 * 1024 * 1024)))
        database.recordTerminals(listOf(HistoryTerminalRecord(matchId, "table", 1000L, false)))
        val before = database.measureDiskUsage().totalBytes
        assertEquals(1, database.pruneMatches(mapOf(matchId to "EXPIRED"), 1001L))
        repeat(4) { database.recoverDiskSpace() }
        val after = database.measureDiskUsage().totalBytes
        assertTrue(after < before)
    }

    /** 第二場次刪除觸發 SQLite 中止時，整批刪除與墓碑必須回滾。 */
    @Test
    fun `test pruning trigger failure rolls back whole batch`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val first = "00000000-0000-0000-0000-000000000108"
        val second = "00000000-0000-0000-0000-000000000109"
        database.appendPendingBatch(listOf(pending(first), pending(second)))
        database.recordTerminals(
            listOf(
                HistoryTerminalRecord(first, "table", 1100L, false),
                HistoryTerminalRecord(second, "table", 1100L, false),
            ),
        )
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """CREATE TRIGGER fail_second_prune BEFORE DELETE ON history_pending_event
                        WHEN OLD.match_id = '$second' BEGIN SELECT RAISE(ABORT, 'test prune failure'); END
                    """.trimIndent(),
                )
            }
        }
        assertFailsWith<Exception> {
            database.pruneMatches(mapOf(first to "EXPIRED", second to "EXPIRED"), 1101L)
        }
        assertTrue(database.readTombstones().isEmpty())
        assertEquals(1, database.readPending(first).size)
        assertEquals(1, database.readPending(second).size)
    }

    /** 已完成摘要與 Replay 具備終端證據時可完整清理，未知場次不會產生墓碑。 */
    @Test
    fun `test completed archive is deleted and unknown row is untouched`() {
        val path = createTempDirectory("mahjongcraft-retention-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val matchId = "00000000-0000-0000-0000-000000000105"
        database.appendPending(pending(matchId))
        database.archive(
            HistoryArchiveRecord(
                matchId, "table", "rule", null, 100L, 200L,
                listOf(HistoryParticipantRecord(0, "player", null)),
                listOf(HistoryRoundRecord(1, 100L, 200L)), "{}",
            ),
        )
        database.recordTerminals(listOf(HistoryTerminalRecord(matchId, "table", 200L, true)))
        assertEquals(0, database.pruneMatches(mapOf("00000000-0000-0000-0000-000000000106" to "EXPIRED"), 900L))
        assertEquals(1, database.pruneMatches(mapOf(matchId to "EXPIRED"), 900L))
        assertEquals(setOf(matchId), database.readTombstones())
        assertTrue(database.readReplayIds().isEmpty())
    }

    /** 建立最小暫存事件。 */
    private fun pending(matchId: String, sequence: Long = 1L): PendingHistoryRecord = PendingHistoryRecord(
        matchId = matchId,
        sequence = sequence,
        roundNumber = 1,
        occurredAtEpochMillis = 100L,
        payloadVersion = 1,
        payload = "{}",
    )
}
