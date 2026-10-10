package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 規則中立的內建胡牌結算模板 key。 */
object BuiltInWinSettlementTemplateKeys {
    /** 規則中立的通用模板；規則沒有綁定模板時使用。 */
    const val GENERIC: String = "${MinecraftModMetadata.MOD_ID}:generic"
}
