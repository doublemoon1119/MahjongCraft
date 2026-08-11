package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry

/**
 * 保存 chunk 載入後的延遲桌子位置驗證工作，不直接依賴 Minecraft 世界 API。
 *
 * [S] 表示 server session，[W] 表示世界參考。外層 adapter 提供 chunk 狀態、位置索引與 BlockEntity
 * 查詢，使排程、session 隔離與第二次確認可獨立測試。
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
     */
    suspend fun advance(
        session: S,
        isChunkUsable: (W, Int, Int) -> Boolean,
        entriesForChunk: (W, Int, Int) -> Collection<TableLocationEntry>,
        isEntryCurrent: (TableLocationEntry) -> Boolean,
        matchesExpectedTable: (W, TableLocationEntry) -> Boolean,
        cleanup: suspend (TableLocationEntry) -> Unit,
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

    /** 尚待第一次定點查詢的 chunk。 */
    private data class PendingChunkLoad<W : Any>(
        /** Chunk 所在世界。 */
        val world: W,
        /** Chunk X 座標。 */
        val chunkX: Int,
        /** Chunk Z 座標。 */
        val chunkZ: Int,
    )

    /** 第一次查詢缺失、等待下一個 tick 再確認的位置。 */
    private data class PendingMissingConfirmation<W : Any>(
        /** 預期位置所在世界。 */
        val world: W,
        /** 排程時的位置與 revision。 */
        val entry: TableLocationEntry,
    )
}
