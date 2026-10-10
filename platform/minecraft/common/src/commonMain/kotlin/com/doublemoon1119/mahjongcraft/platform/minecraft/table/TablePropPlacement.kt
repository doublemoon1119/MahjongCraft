package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 一個桌面物件的擺放描述。
 *
 * 兩次描述之間，種類、變體、座位、錨點、偏移與轉角都相同的物件視為同一個：已經在桌上的會保留，不會重新
 * 生成或重播出現動畫。
 *
 * @property kind 物件種類的命名空間識別碼，由平台端的物件種類登錄處決定實際生成的 entity。
 * @property variant 種類內的變體，例如點棒的面額；意義由該種類自行解讀。
 * @property seatIndex 所屬座位，即 `TableState.players` 的 index。
 * @property anchor 擺放的基準位置。
 * @property offset 相對於 [anchor] 的偏移。
 * @property yawOffset 在座位原本朝向之上額外轉動的角度（度）。
 */
data class TablePropPlacement(
    val kind: String,
    val variant: String,
    val seatIndex: Int,
    val anchor: TableSeatAnchor,
    val offset: TableSeatOffset = TableSeatOffset(),
    val yawOffset: Float = 0f,
) {
    init {
        MinecraftResourceIds.requireValid(kind) { "Table prop kind must be namespaced: $kind" }
        require(seatIndex >= 0) { "Table prop seat index must not be negative: $seatIndex" }
    }
}
