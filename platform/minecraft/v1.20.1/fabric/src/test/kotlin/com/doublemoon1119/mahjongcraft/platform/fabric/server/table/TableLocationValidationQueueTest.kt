package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [TableLocationValidationQueue] 的 tick、session 與 revision 邊界測試。 */
class TableLocationValidationQueueTest {
    /** 第一次查詢缺失只排入確認，下一個 tick 仍缺失才執行清理。 */
    @Test
    fun `missing table is cleaned only after confirmation tick`() = runTest {
        val fixture = Fixture()
        fixture.queue.enqueueChunk(fixture.world, 0, 0)

        fixture.advance()
        fixture.advance()
        assertTrue(fixture.cleanedEntries.isEmpty())

        fixture.advance()
        assertEquals(listOf(fixture.entry), fixture.cleanedEntries)
    }

    /** 第二次確認前出現符合 UUID 的桌子時不得清理。 */
    @Test
    fun `table restored before confirmation cancels cleanup`() = runTest {
        val fixture = Fixture()
        fixture.queue.enqueueChunk(fixture.world, 0, 0)

        fixture.advance()
        fixture.advance()
        fixture.tableMatches = true
        fixture.advance()

        assertTrue(fixture.cleanedEntries.isEmpty())
    }

    /** 第二次確認前位置 revision 改變時不得清理新位置。 */
    @Test
    fun `changed revision cancels stale cleanup`() = runTest {
        val fixture = Fixture()
        fixture.queue.enqueueChunk(fixture.world, 0, 0)

        fixture.advance()
        fixture.advance()
        fixture.entryIsCurrent = false
        fixture.advance()

        assertTrue(fixture.cleanedEntries.isEmpty())
    }

    /** 停止 session 應清除所有工作，舊 session 後續 tick 不得處理。 */
    @Test
    fun `stopping session clears pending requests`() = runTest {
        val fixture = Fixture()
        fixture.queue.enqueueChunk(fixture.world, 0, 0)
        fixture.advance()

        assertEquals(1, fixture.queue.stopSession())
        fixture.advance()

        assertEquals(0, fixture.queue.pendingCount)
        assertTrue(fixture.cleanedEntries.isEmpty())
    }

    /** 建立不依賴 Minecraft 世界類別的驗證排程測試資料。 */
    private class Fixture {
        /** 測試 session。 */
        val session = Any()

        /** 測試世界參考。 */
        val world = Any()

        /** 預期桌子位置。 */
        val entry = TableLocationEntry(
            tableId = Uuid.random(),
            location = TableLocation("minecraft:overworld", 0, 64, 0),
            revision = 1,
        )

        /** 受測排程。 */
        val queue = TableLocationValidationQueue<Any, Any>().apply { startSession(session) }

        /** 第二次確認時位置是否仍為目前 revision。 */
        var entryIsCurrent = true

        /** 預期位置是否已有相符桌子。 */
        var tableMatches = false

        /** 已執行清理的位置。 */
        val cleanedEntries = mutableListOf<TableLocationEntry>()

        /** 推進一次 server tick 邊界。 */
        suspend fun advance() {
            queue.advance(
                session = session,
                isChunkUsable = { _, _, _ -> true },
                entriesForChunk = { _, _, _ -> listOf(entry) },
                isEntryCurrent = { entryIsCurrent },
                matchesExpectedTable = { _, _ -> tableMatches },
                cleanup = cleanedEntries::add,
            )
        }
    }
}
