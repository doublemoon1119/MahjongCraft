package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions

/** 登記通用模板，以及所有內建規則 extension 的模板與欄位。 */
internal fun WinSettlementPresentationTemplateRegistry.registerBundledWinSettlementTemplates() {
    BuiltInMinecraftMahjongExtension.registerWinSettlementPresentationTemplates(this)
    BundledMinecraftMahjongExtensions.all.forEach { it.registerWinSettlementPresentationTemplates(this) }
}
