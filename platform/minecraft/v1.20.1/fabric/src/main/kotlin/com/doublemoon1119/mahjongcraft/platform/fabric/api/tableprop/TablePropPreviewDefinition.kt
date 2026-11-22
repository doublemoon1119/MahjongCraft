package com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition

/**
 * 客戶端初始化時登記的一種桌上物件預覽。
 *
 * @property definition 與伺服器登記相同的種類定義，提供識別碼與擺放用的尺寸。
 * @property provider 建立不加入世界的實體的工廠。
 */
class TablePropPreviewDefinition(
    val definition: TablePropKindDefinition,
    val provider: TablePropPreviewProvider,
) {
    /** 物件種類識別碼。 */
    val id: String get() = definition.id
}
