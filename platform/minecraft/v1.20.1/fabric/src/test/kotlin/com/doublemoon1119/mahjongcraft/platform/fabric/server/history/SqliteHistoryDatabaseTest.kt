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

        assertFailsWith<IllegalStateException> { SqliteHistoryDatabase.open(path) }
        assertTrue(Files.exists(path))
    }

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
