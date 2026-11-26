package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 已知麻將桌位置與防止延遲工作誤用舊資料的 revision。
 *
 * @property tableId 麻將桌穩定 UUID。
 * @property location 麻將桌最後確認的位置。
 * @property revision 每次同一桌位置改變時遞增的版本。
 */
data class TableLocationEntry(
    val tableId: Uuid,
    val location: TableLocation,
    val revision: Long,
) {
    init {
        require(revision > 0) { "Table location revision must be positive" }
    }
}
