package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 每存檔路徑、schema v1 與暫存事件去重測試。 */
class SqliteHistoryDatabaseTest {
    /** 同一存檔不論遊戲維度，都只使用存檔根目錄下的資料庫。 */
    @Test
    fun `test database path belongs to save root`() {
        val firstSave = Path.of("first-save")
        val secondSave = Path.of("second-save")

        assertEquals(firstSave.resolve("mahjongcraft/history.sqlite"), FabricHistoryDatabasePath.resolve(firstSave))
        assertEquals(secondSave.resolve("mahjongcraft/history.sqlite"), FabricHistoryDatabasePath.resolve(secondSave))
    }

    /** 新檔建立 schema；重新開啟後仍可讀取且相同事件重送不增加筆數。 */
    @Test
    fun `test schema and pending events survive reopening`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("world/mahjongcraft/history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val later = pending(sequence = 2, payload = "{\"second\":true}")
        val earlier = pending(sequence = 1, payload = "{\"first\":true}")

        database.appendPending(later)
        database.appendPending(earlier)
        database.appendPending(earlier)

        assertTrue(Files.exists(path))
        assertEquals(listOf(earlier, later), SqliteHistoryDatabase.open(path).readPending(earlier.matchId))
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM history_schema_version").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(SqliteHistoryDatabase.SCHEMA_VERSION, rows.getInt(1))
                }
            }
        }
    }

    /** 同鍵不同內容必須報錯，不覆寫資料庫內原本的權威事件。 */
    @Test
    fun `test conflicting pending event is rejected`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val original = pending(sequence = 1, payload = "original")
        database.appendPending(original)

        assertFailsWith<IllegalStateException> {
            database.appendPending(original.copy(payload = "different"))
        }
        assertEquals(listOf(original), database.readPending(original.matchId))
    }

    /** 批次末尾發生衝突時，前面新插入的事件也不得留下。 */
    @Test
    fun `test conflicting batch rolls back all new events`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val original = pending(sequence = 1, payload = "original")
        database.appendPending(original)

        assertFailsWith<IllegalStateException> {
            database.appendPendingBatch(
                listOf(pending(sequence = 2, payload = "new"), original.copy(payload = "different")),
            )
        }
        assertEquals(listOf(original), database.readPending(original.matchId))
    }

    /** 未知較新 schema 不得被改寫為目前版本。 */
    @Test
    fun `test newer schema is preserved`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        SqliteHistoryDatabase.open(path)
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { it.executeUpdate("UPDATE history_schema_version SET version = 2") }
        }

        assertFailsWith<IllegalStateException> { SqliteHistoryDatabase.open(path) }
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM history_schema_version").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(2, rows.getInt(1))
                }
            }
        }
    }

    /** 損壞與未知的既有檔案都應保留，不能當作空資料庫重新建立。 */
    @Test
    fun `test damaged existing file is not replaced`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val original = "not a sqlite database".toByteArray()
        Files.write(path, original)

        assertFailsWith<Exception> { SqliteHistoryDatabase.open(path) }
        assertTrue(original.contentEquals(Files.readAllBytes(path)))
    }

    /** 即使既有檔案是空檔，也不能默默當作首次建庫而覆寫。 */
    @Test
    fun `test empty existing file is not initialized`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        Files.createFile(path)

        assertFailsWith<IllegalStateException> { SqliteHistoryDatabase.open(path) }
        assertTrue(Files.exists(path))
    }

    /** 宣稱 v1 卻缺少資料表時，保留原檔並拒絕使用。 */
    @Test
    fun `test missing schema table is rejected`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        SqliteHistoryDatabase.open(path)
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { it.executeUpdate("DROP TABLE history_replay") }
        }

        val failure = assertFailsWith<IllegalStateException> { SqliteHistoryDatabase.open(path) }
        assertEquals("History database is missing required schema tables: history_replay", failure.message)
        assertTrue(Files.exists(path))
    }

    /** 摘要、Replay 與原始事件刪除必須共用交易；缺口場次不可封存。 */
    @Test
    fun `test archive is atomic and refuses known gaps`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val event = pending(sequence = 1, payload = "original")
        database.appendPending(event)
        val archive = archive(event.matchId)

        database.recordGaps(mapOf(event.matchId to 2L))
        assertFailsWith<IllegalStateException> { database.archive(archive) }
        assertEquals(listOf(event), database.readPending(event.matchId))
        assertTrue(database.readReplayIds().isEmpty())
    }

    /** 錄製停止診斷可跨重新開啟保留，且相同原因重送具備冪等性。 */
    @Test
    fun `test recording stops survive reopening and are idempotent`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val matchId = "00000000-0000-0000-0000-000000000003"
        val stops = mapOf(matchId to "PARTIAL_CONFIG_DISABLED")

        database.recordRecordingStops(stops)
        database.recordRecordingStops(stops)

        assertEquals(stops, SqliteHistoryDatabase.open(path).readRecordingStops())
    }

    /** 缺少新增資料表的既有資料庫必須拒絕使用且保留原檔。 */
    @Test
    fun `test existing schema missing recording stop table is rejected without reset`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val event = pending(sequence = 1, payload = "original")
        database.appendPending(event)
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { it.executeUpdate("DROP TABLE history_recording_stop") }
        }

        val failure = assertFailsWith<IllegalStateException> { SqliteHistoryDatabase.open(path) }
        assertEquals("History database is missing required schema tables: history_recording_stop", failure.message)
        assertTrue(Files.exists(path))
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT payload FROM history_pending_event").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(event.payload, rows.getString(1))
                }
            }
        }
    }

    /** 已記錄停止診斷的對局即使沒有事件缺口，也不得封存。 */
    @Test
    fun `test archive refuses recording stop without a gap`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val event = pending(sequence = 1, payload = "original")
        database.appendPending(event)
        database.recordRecordingStops(mapOf(event.matchId to "PARTIAL_CONFIG_DISABLED"))

        assertFailsWith<IllegalStateException> { database.archive(archive(event.matchId)) }
        assertEquals(listOf(event), database.readPending(event.matchId))
        assertTrue(database.readReplayIds().isEmpty())
    }

    /** 成功封存後重新開庫，Replay 與摘要保留且原始事件已移除。 */
    @Test
    fun `test archive survives reopening without pending events`() {
        val path = createTempDirectory("mahjongcraft-history-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val event = pending(sequence = 1, payload = "original")
        database.appendPending(event)

        assertTrue(database.archive(archive(event.matchId)))
        val reopened = SqliteHistoryDatabase.open(path)
        assertEquals(emptyList(), reopened.readPending(event.matchId))
        assertEquals(setOf(event.matchId), reopened.readReplayIds())
    }

    /** 最小但具備所有關聯列的封存測資。 */
    private fun archive(matchId: String): HistoryArchiveRecord = HistoryArchiveRecord(
        matchId = matchId,
        tableId = "00000000-0000-0000-0000-000000000002",
        ruleId = "mahjongcraft:riichi",
        dimensionId = null,
        startedAtEpochMillis = 100L,
        endedAtEpochMillis = 200L,
        participants = listOf(HistoryParticipantRecord(0, "player-1", null)),
        rounds = listOf(HistoryRoundRecord(1, 100L, 200L)),
        replayPayload = "{}",
    )

    /** 建立固定的測試事件。 */
    private fun pending(sequence: Long, payload: String): PendingHistoryRecord = PendingHistoryRecord(
        matchId = "00000000-0000-0000-0000-000000000001",
        sequence = sequence,
        roundNumber = 1,
        occurredAtEpochMillis = 1234L,
        payloadVersion = 1,
        payload = payload,
    )
}
