package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 一個桌面物件的擺放描述。
 *
 * ID 在同一桌內代表同一個規則物件，不隨位置或內容改變。內容與落點也相符時保留既有實體；
 * 需要更換內容或位置時替換實體，不冒用正式牌張身分。
 *
 * @property id 規則提供的穩定識別碼；同一桌與呈現世代內不得重複。
 * @property kind 物件種類的命名空間識別碼。
 * @property content 種類要呈現的型別化內容。
 * @property location 平台定義的座位或中央位置。
 */
data class TablePropPlacement(
    val id: String,
    val kind: String,
    val content: TablePropContent,
    val location: TablePropLocation,
) {
    init {
        MinecraftResourceIds.requireValid(id) { "Table prop ID must be namespaced: $id" }
        MinecraftResourceIds.requireValid(kind) { "Table prop kind must be namespaced: $kind" }
    }
}
