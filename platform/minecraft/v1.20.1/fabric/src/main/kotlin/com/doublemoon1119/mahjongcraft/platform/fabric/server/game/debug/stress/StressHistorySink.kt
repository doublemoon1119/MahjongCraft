package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import java.nio.file.Files
import java.nio.file.Path

/** 處理壓力測試待寫佇列的背景工作。 */
sealed interface StressHistorySink {
    /** 是否發生錯誤或暫停。 */
    suspend fun failed(): Boolean

    /** 停止背景工作並刪除產生的檔案。 */
    suspend fun close()

    /**
     * 以正式寫入流程寫進獨立資料庫。
     *
     * @property writer 背景寫入元件。
     * @property databasePath 壓力測試資料庫檔案。
     */
    class Database(
        val writer: FabricHistoryOutboxWriter,
        val databasePath: Path,
    ) : StressHistorySink {
        override suspend fun failed(): Boolean = writer.status().let { !it.databaseConnected || it.storagePaused || it.lastError != null }
        override suspend fun close() {
            writer.detach()
            DATABASE_FILE_SUFFIXES.forEach { suffix -> Files.deleteIfExists(databasePath.resolveSibling(databasePath.fileName.toString() + suffix)) }
        }
        private companion object {
            /** 資料庫主檔與 SQLite WAL／SHM 附屬檔的檔名後綴。 */
            val DATABASE_FILE_SUFFIXES: List<String> = listOf("", "-wal", "-shm")
        }
    }

    /**
     * 以與寫入元件相同的批次大小與輪詢間隔，把待寫事件編碼成持久化格式後直接確認移除；場次結束後的記錄狀態也一併確認，
     * 如同已封存。
     *
     * @property job 背景工作。
     * @property failure 背景工作是否已因錯誤停止。
     */
    class EncodeOnly internal constructor(
        private val job: Job,
        private val failure: () -> Boolean,
    ) : StressHistorySink {
        override suspend fun failed(): Boolean = failure()
        override suspend fun close() = job.cancelAndJoin()
    }

    /** 不記錄歷史，沒有背景工作。 */
    data object Disabled : StressHistorySink {
        override suspend fun failed(): Boolean = false
        override suspend fun close() = Unit
    }
}
