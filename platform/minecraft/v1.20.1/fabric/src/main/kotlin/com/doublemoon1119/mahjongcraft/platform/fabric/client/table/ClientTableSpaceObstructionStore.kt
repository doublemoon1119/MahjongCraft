package com.doublemoon1119.mahjongcraft.platform.fabric.client.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSpace
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceCellDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceObstructionPayloadDto
import kotlin.uuid.Uuid

/**
 * 保存伺服器告知的、等待開局桌子被擋住的擺牌格，只在主執行緒存取。
 *
 * 供世界中的紅色線框與房間畫面的開始按鈕使用；伺服器在真正開局前仍會再檢查一次。
 */
class ClientTableSpaceObstructionStore {
    /** 每張桌子目前被擋住的格子與所在維度。 */
    private val byTable = mutableMapOf<Uuid, Obstruction>()

    /**
     * 套用伺服器送來的標示；格子為空時清除該桌，格子數超過擺牌層或桌子 ID 無效時忽略。
     *
     * @param payload 伺服器送來的標示。
     */
    fun apply(payload: TableSpaceObstructionPayloadDto) {
        val tableId = runCatching { Uuid.parse(payload.tableId) }.getOrNull() ?: return
        if (payload.cells.size > MahjongTableSpace.playSpaceHorizontalOffsets.size) return
        if (payload.cells.isEmpty()) {
            byTable.remove(tableId)
        } else {
            byTable[tableId] = Obstruction(payload.dimensionId, payload.cells)
        }
    }

    /**
     * 指定桌子目前是否被擋住。
     *
     * @param tableId 桌子 UUID。
     * @return 有被擋住的格子時為 true。
     */
    fun isObstructed(tableId: Uuid): Boolean = tableId in byTable

    /**
     * 列出指定維度中所有被擋住的格子。
     *
     * @param dimensionId 維度識別碼。
     * @return 該維度中所有桌子被擋住的格子。
     */
    fun cellsIn(dimensionId: String): List<TableSpaceCellDto> = byTable.values
        .filter { it.dimensionId == dimensionId }
        .flatMap { it.cells }

    /**
     * 清除一張桌子的標示，例如桌子被移除時。
     *
     * @param tableId 桌子 UUID。
     */
    fun clearTable(tableId: Uuid) {
        byTable.remove(tableId)
    }

    /** 斷線或切換世界時清除所有標示。 */
    fun clear() {
        byTable.clear()
    }

    /**
     * 一張桌子被擋住的格子。
     *
     * @property dimensionId 桌子所在維度。
     * @property cells 被擋住的格子。
     */
    private data class Obstruction(
        val dimensionId: String,
        val cells: List<TableSpaceCellDto>,
    )
}
