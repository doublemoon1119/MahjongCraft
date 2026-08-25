package com.doublemoon1119.mahjongcraft.platform.minecraft.rule

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/**
 * 註冊內建規則模組（日麻、台麻）的顯示名稱，識別碼與規則註冊共用 [BuiltInRuleModuleIds]。
 */
fun RuleModuleDisplayNameRegistry.registerBuiltInRuleModuleDisplayNames() {
    register(BuiltInRuleModuleIds.RIICHI, MinecraftMessageKeys.RULE_MODULE_RIICHI)
    register(BuiltInRuleModuleIds.TAIWAN, MinecraftMessageKeys.RULE_MODULE_TAIWAN)
}
