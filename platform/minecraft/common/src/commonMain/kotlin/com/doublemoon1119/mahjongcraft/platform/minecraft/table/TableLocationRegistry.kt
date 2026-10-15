package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 保存單次 server save 的麻將桌最後已知位置，並提供依 chunk 查詢的記憶體索引。
 *
 * 變更與 chunk 索引查詢必須由 Minecraft server thread 呼叫；[snapshot] 與 [get] 讀取
 * 不可變 map 的已發布版本，可供歷史 I/O 工作取得位置摘要。已保存位置無法讀取時以 [markUnavailable] 拒絕
 * 登記與移除，直到下一次 [load] 或 [clear]。
 */
class TableLocationRegistry {
    /** 以桌子 UUID 索引的持久化資料。 */
    @Volatile private var entriesByTableId: Map<Uuid, TableLocationEntry> = emptyMap()

    /** 由持久化資料重建、不另外保存的 chunk 反向索引。 */
    private var tableIdsByChunk: Map<DimensionChunkKey, Set<Uuid>> = emptyMap()

    /** 位置資料實際改變時通知平台 persistence adapter。 */
    private var dirtyListener: (Map<Uuid, TableLocationEntry>) -> Unit = {}

    /** 目前 session 的位置資料是否可用；不可用時登記與移除都會拋出 [TableLocationsUnavailableException]。 */
    @Volatile var isAvailable: Boolean = true
        private set

    /**
     * 取得指定桌子的目前位置資料。
     *
     * @param tableId 麻將桌 UUID。
     * @return 目前位置與 revision；未登記時為 `null`。
     */
    fun get(tableId: Uuid): TableLocationEntry? = entriesByTableId[tableId]

    /**
     * 取得指定 dimension chunk 內所有預期桌子的位置資料。
     *
     * @param key dimension 與 chunk 座標。
     * @return 該 chunk 內已登記桌子的位置資料。
     */
    fun getByChunk(key: DimensionChunkKey): List<TableLocationEntry> = tableIdsByChunk[key]
        .orEmpty()
        .mapNotNull(entriesByTableId::get)

    /** 取得目前所有位置資料的不可變快照。 */
    fun snapshot(): Map<Uuid, TableLocationEntry> = entriesByTableId.toMap()

    /**
     * 載入已保存資料、重建反向索引並恢復可用，不觸發 dirty callback。
     *
     * @param entries 從存檔還原的位置資料；桌子 UUID 必須唯一。
     */
    fun load(entries: Collection<TableLocationEntry>) {
        isAvailable = true
        require(entries.map(TableLocationEntry::tableId).distinct().size == entries.size) {
            "Table location data contains duplicate table IDs"
        }
        entriesByTableId = entries.associateBy(TableLocationEntry::tableId)
        rebuildChunkIndex()
    }

    /**
     * 登記或修正桌子位置。
     *
     * 相同位置不改變 revision 或 dirty 狀態；位置改變時 revision 遞增。
     *
     * @param tableId 麻將桌 UUID。
     * @param location 新確認的位置。
     * @return 已登記的位置與 revision。
     * @throws TableLocationsUnavailableException 已呼叫 [markUnavailable] 時拋出。
     */
    fun put(tableId: Uuid, location: TableLocation): TableLocationEntry {
        if (!isAvailable) throw TableLocationsUnavailableException()
        val existing = entriesByTableId[tableId]
        if (existing?.location == location) return existing

        val next = TableLocationEntry(tableId, location, (existing?.revision ?: 0L) + 1L)
        entriesByTableId = entriesByTableId + (tableId to next)
        rebuildChunkIndex()
        dirtyListener(entriesByTableId)
        return next
    }

    /**
     * 只有 revision 仍符合延遲工作的預期時才移除位置。
     *
     * @param tableId 麻將桌 UUID。
     * @param expectedRevision 延遲工作預期的 revision；`null` 表示不比對版本。
     * @return 確實移除資料時為 `true`。
     * @throws TableLocationsUnavailableException 已呼叫 [markUnavailable] 時拋出。
     */
    fun remove(tableId: Uuid, expectedRevision: Long? = null): Boolean {
        if (!isAvailable) throw TableLocationsUnavailableException()
        val existing = entriesByTableId[tableId] ?: return false
        if (expectedRevision != null && existing.revision != expectedRevision) return false
        entriesByTableId = entriesByTableId - tableId
        rebuildChunkIndex()
        dirtyListener(entriesByTableId)
        return true
    }

    /**
     * 設定位置改變 callback。
     *
     * @param listener 資料變更後接收目前完整快照的 callback。
     */
    fun setDirtyListener(listener: (Map<Uuid, TableLocationEntry>) -> Unit) {
        dirtyListener = listener
    }

    /**
     * 標記目前 session 的已保存位置無法讀取，之後登記與移除都拋出 [TableLocationsUnavailableException]，
     * 直到下一次 [load] 或 [clear]。
     */
    fun markUnavailable() {
        isAvailable = false
    }

    /** 清除目前 session 的記憶體資料並恢復可用，不觸發 dirty callback。 */
    fun clear() {
        isAvailable = true
        entriesByTableId = emptyMap()
        tableIdsByChunk = emptyMap()
    }

    /** 依目前位置資料完整重建 chunk 反向索引。 */
    private fun rebuildChunkIndex() {
        tableIdsByChunk = entriesByTableId.values
            .groupBy { entry ->
                DimensionChunkKey(
                    entry.location.dimensionId,
                    entry.location.chunkX,
                    entry.location.chunkZ,
                )
            }
            .mapValues { (_, entries) -> entries.map(TableLocationEntry::tableId).toSet() }
    }
}
