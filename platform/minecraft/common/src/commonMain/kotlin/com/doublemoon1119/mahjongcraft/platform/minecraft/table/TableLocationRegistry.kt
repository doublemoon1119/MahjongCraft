package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 保存單次 server save 的麻將桌最後已知位置，並提供依 chunk 查詢的記憶體索引。
 *
 * 所有存取必須由 Minecraft server thread 呼叫。
 */
class TableLocationRegistry {
    /** 以桌子 UUID 索引的持久化資料。 */
    private var entriesByTableId: Map<Uuid, TableLocationEntry> = emptyMap()

    /** 由持久化資料重建、不另外保存的 chunk 反向索引。 */
    private var tableIdsByChunk: Map<DimensionChunkKey, Set<Uuid>> = emptyMap()

    /** 位置資料實際改變時通知平台 persistence adapter。 */
    private var dirtyListener: (Map<Uuid, TableLocationEntry>) -> Unit = {}

    /** 取得指定桌子的目前位置資料。 */
    fun get(tableId: Uuid): TableLocationEntry? = entriesByTableId[tableId]

    /** 取得指定 dimension chunk 內所有預期桌子的位置資料。 */
    fun getByChunk(key: DimensionChunkKey): List<TableLocationEntry> = tableIdsByChunk[key]
        .orEmpty()
        .mapNotNull(entriesByTableId::get)

    /** 取得目前所有位置資料的不可變快照。 */
    fun snapshot(): Map<Uuid, TableLocationEntry> = entriesByTableId

    /** 載入已保存資料並重建反向索引，不觸發 dirty callback。 */
    fun load(entries: Collection<TableLocationEntry>) {
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
     */
    fun put(tableId: Uuid, location: TableLocation): TableLocationEntry {
        val existing = entriesByTableId[tableId]
        if (existing?.location == location) return existing

        val next = TableLocationEntry(tableId, location, (existing?.revision ?: 0L) + 1L)
        entriesByTableId = entriesByTableId + (tableId to next)
        rebuildChunkIndex()
        dirtyListener(entriesByTableId)
        return next
    }

    /** 只有 revision 仍符合延遲工作的預期時才移除位置。 */
    fun remove(tableId: Uuid, expectedRevision: Long? = null): Boolean {
        val existing = entriesByTableId[tableId] ?: return false
        if (expectedRevision != null && existing.revision != expectedRevision) return false
        entriesByTableId = entriesByTableId - tableId
        rebuildChunkIndex()
        dirtyListener(entriesByTableId)
        return true
    }

    /** 設定位置改變 callback。 */
    fun setDirtyListener(listener: (Map<Uuid, TableLocationEntry>) -> Unit) {
        dirtyListener = listener
    }

    /** 清除目前 session 的記憶體資料，不觸發 dirty callback。 */
    fun clear() {
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
