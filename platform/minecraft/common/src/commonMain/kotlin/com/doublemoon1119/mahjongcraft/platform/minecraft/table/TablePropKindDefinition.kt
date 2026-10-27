package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 一種桌面物件的共用描述。
 *
 * 這個型別只包含規則與平台都需要的資料，不建立平台原生物件。原生種類應以同一份定義驗證變體、尺寸與內容。
 * 內容驗證只依傳入內容回傳結果，不讀寫世界、不阻塞，也不依賴可變外部狀態。
 *
 * @property id 種類的完整命名空間識別碼。
 * @property dimensions 物件在桌面上實際占用的尺寸。
 * @property acceptsContent 驗證種類是否能呈現指定內容的純函式。
 */
data class TablePropKindDefinition(
    val id: String,
    val dimensions: TablePropDimensions,
    val acceptsContent: (TablePropContent) -> Boolean = { it is TablePropContent.CustomVariant },
) {
    init {
        MinecraftResourceIds.requireValid(id) { "Table prop kind must be namespaced: $id" }
    }

    /**
     * 判斷種類是否接受指定內容。
     *
     * @param content 要驗證的內容。
     * @return 內容可接受時為 `true`。
     */
    fun accepts(content: TablePropContent): Boolean = acceptsContent(content)
}
