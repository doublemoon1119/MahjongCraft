package com.doublemoon1119.mahjongcraft.platform.minecraft.rule

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/**
 * 註冊內建規則模組（日麻、台麻）的顯示名稱。id 沿用 `mahjong-flow-common` 的
 * `registerBuiltInRuleModules()` 已經在用的 `"mahjongcraft:riichi"`／`"mahjongcraft:taiwan"`，不重新
 * 定義一套識別碼（`platform/minecraft/common` 不依賴 `mahjong-flow-common`，這裡故意不用型別參照，
 * 只用純文字說明對應關係）。
 */
fun RuleModuleDisplayNameRegistry.registerBuiltInRuleModuleDisplayNames() {
    register("mahjongcraft:riichi", MinecraftMessageKeys.RULE_MODULE_RIICHI)
    register("mahjongcraft:taiwan", MinecraftMessageKeys.RULE_MODULE_TAIWAN)
}
