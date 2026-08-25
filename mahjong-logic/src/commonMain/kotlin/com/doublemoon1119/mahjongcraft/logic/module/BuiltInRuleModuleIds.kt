package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** MahjongCraft 內建規則模組的穩定識別碼。 */
object BuiltInRuleModuleIds {
    /** 日本麻將規則模組。 */
    val RIICHI: String = MahjongCraftMetadata.id("riichi")

    /** 台灣麻將規則模組。 */
    val TAIWAN: String = MahjongCraftMetadata.id("taiwan")
}
