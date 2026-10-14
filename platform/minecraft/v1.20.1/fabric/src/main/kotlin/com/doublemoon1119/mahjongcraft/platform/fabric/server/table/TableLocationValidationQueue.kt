package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry

/**
 * 保存 chunk 載入後的延遲桌子位置驗證工作，不直接依賴 Minecraft 世界 API。
 *
 * [S] 表示 server session，[W] 表示世界參考。外層 adapter 提供 chunk 狀態、位置索引與 BlockEntity
 * 查詢，使排程、session 隔離與第二次確認可獨立測試。
 *
 * @param S 代表伺服器 session 的參考型別。
 * @param W 代表世界的參考型別。
 */
internal class TableLocationValidationQueue<S : Any, W : Any> {
    /** 目前允許處理工作的 server session。 */
    private var activeSession: S? = null

    /** 下一個 tick 邊界才可處理的 chunk 載入工作。 */
    private var nextChunkLoads = mutableListOf<PendingChunkLoad<W>>()

    /** 本次 tick 邊界要處理的 chunk 載入工作。 */
    private var readyChunkLoads = mutableListOf<PendingChunkLoad<W>>()

    /** 下一個 tick 邊界再次確認的缺失位置。 */
    private var nextMissingConfirmations = mutableListOf<PendingMissingConfirmation<W>>()

    /** 本次 tick 邊界要再次確認的缺失位置。 */
    private var readyMissingConfirmations = mutableListOf<PendingMissingConfirmation<W>>()

    /** 啟用 [session]；啟動期間已排入的 chunk 工作維持不變。 */
    fun startSession(session: S) {
        activeSession = session
    }

    /** 停止目前 session、清除所有工作並回傳清除數量。 */
    fun stopSession(): Int {
        val pendingCount = pendingCount
        activeSession = null
        nextChunkLoads.clear()
        readyChunkLoads.clear()
        nextMissingConfirmations.clear()
        readyMissingConfirmations.clear()
        return pendingCount
    }

    /** 將已載入的 [world] chunk 排入下一個 tick 邊界。 */
    fun enqueueChunk(world: W, chunkX: Int, chunkZ: Int) {
        nextChunkLoads.add(PendingChunkLoad(world, chunkX, chunkZ))
    }

    /**
     * 處理上一輪已就緒的工作，再把本輪工作移到下一輪。
     *
     * 第一次定點查詢缺失時只排入確認；下一個 tick 仍符合 session、chunk、revision 與缺失條件才呼叫
     * [cleanup]。
     *
     * @param session 目前 tick 所屬的 server session。
     * @param isChunkUsable 判斷 chunk 是否仍在 session 中載入。
     * @param entriesForChunk 取得該 chunk 的預期位置索引。
     * @param isEntryCurrent 確認索引項目的 revision 尚未改變。
     * @param matchesExpectedTable 驗證方塊位置仍有預期桌子。
     * @param cleanup 第二次確認仍缺失時執行的清理操作；在呼叫端執行緒同步呼叫，不得等待協程鎖。
     */
    fun advance(
        session: S,
        isChunkUsable: (W, Int, Int) -> Boolean,
        entriesForChunk: (W, Int, Int) -> Collection<TableLocationEntry>,
        isEntryCurrent: (TableLocationEntry) -> Boolean,
        matchesExpectedTable: (W, TableLocationEntry) -> Boolean,
        cleanup: (TableLocationEntry) -> Unit,
    ) {
        if (activeSession !== session) return

        readyChunkLoads.forEach { request ->
            if (isChunkUsable(request.world, request.chunkX, request.chunkZ)) {
                entriesForChunk(request.world, request.chunkX, request.chunkZ).forEach { entry ->
                    if (!matchesExpectedTable(request.world, entry)) {
                        nextMissingConfirmations.add(PendingMissingConfirmation(request.world, entry))
                    }
                }
            }
        }
        readyMissingConfirmations.forEach { request ->
            val location = request.entry.location
            if (
                isChunkUsable(request.world, location.chunkX, location.chunkZ) &&
                isEntryCurrent(request.entry) &&
                !matchesExpectedTable(request.world, request.entry)
            ) {
                cleanup(request.entry)
            }
        }

        readyChunkLoads = nextChunkLoads.also { nextChunkLoads = mutableListOf() }
        readyMissingConfirmations = nextMissingConfirmations.also { nextMissingConfirmations = mutableListOf() }
    }

    /** 尚未處理的 chunk 與缺失確認工作總數。 */
    val pendingCount: Int
        get() = nextChunkLoads.size + readyChunkLoads.size +
            nextMissingConfirmations.size + readyMissingConfirmations.size

    /**
     * 尚待第一次定點查詢的 chunk。
     *
     * @property world chunk 所在世界。
     * @property chunkX chunk X 座標。
     * @property chunkZ chunk Z 座標。
     */
    private data class PendingChunkLoad<W : Any>(
        val world: W,
        val chunkX: Int,
        val chunkZ: Int,
    )

    /**
     * 第一次查詢缺失、等待下一個 tick 再確認的位置。
     *
     * @property world 預期位置所在世界。
     * @property entry 排程時的位置與 revision。
     */
    private data class PendingMissingConfirmation<W : Any>(
        val world: W,
        val entry: TableLocationEntry,
    )
}
