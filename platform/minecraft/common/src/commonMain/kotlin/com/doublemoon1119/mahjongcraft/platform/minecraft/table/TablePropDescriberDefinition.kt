package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 將規則模組 ID 綁定到桌面物件描述器，供平台取得呈現資料。
 *
 * @property ruleModuleId 適用規則模組的穩定識別碼。
 * @property describer 提供桌面描述的來源；登記時不呼叫。
 */
data class TablePropDescriberDefinition(
    val ruleModuleId: String,
    val describer: TablePropDescriber,
)
