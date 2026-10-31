package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlinx.serialization.Serializable

/**
 * 等待開局的桌子目前被擋住的擺牌格，送給該房間的成員。
 *
 * @property tableId 桌子 UUID 字串。
 * @property dimensionId 桌子所在維度的識別碼。
 * @property cells 被擋住的格子，最多為擺牌層的 9 格；空清單表示已經沒有擋住，客戶端清除該桌的標示。
 */
@Serializable
data class TableSpaceObstructionPayloadDto(
    val tableId: String,
    val dimensionId: String,
    val cells: List<TableSpaceCellDto>,
)
