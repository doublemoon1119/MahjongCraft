package com.doublemoon1119.mahjongcraft.platform.minecraft.rule

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/**
 * 註冊內建規則模組（日麻、台麻）的顯示名稱，識別碼與規則註冊共用 [BuiltInRuleModuleIds]。
 */
fun RuleModuleDisplayNameRegistry.registerBuiltInRuleModuleDisplayNames() {
    registerRiichiRuleModuleDisplayName()
    registerTaiwanRuleModuleDisplayName()
}

/** 註冊日麻規則模組的顯示名稱。 */
fun RuleModuleDisplayNameRegistry.registerRiichiRuleModuleDisplayName() {
    register(BuiltInRuleModuleIds.RIICHI, MinecraftMessageKeys.RULE_MODULE_RIICHI)
}

/** 註冊台麻規則模組的顯示名稱。 */
fun RuleModuleDisplayNameRegistry.registerTaiwanRuleModuleDisplayName() {
    register(BuiltInRuleModuleIds.TAIWAN, MinecraftMessageKeys.RULE_MODULE_TAIWAN)
}
