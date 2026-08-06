package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule

/**
 * 註冊 `:mahjong-flow` 內建支援的規則模組（日麻、台麻）。
 *
 * 放在 `:mahjong-flow-common`：內建規則清單為 client、server 共用，且 `:mahjong-flow-client`
 * 不依賴 `:mahjong-flow-server`。註冊方式與第三方規則相同，皆透過 [MahjongModuleRegistry.register]，
 * 不具特權；第三方規則可在各自的組裝處另行呼叫 `registry.register(...)`。
 */
fun MahjongModuleRegistry.registerBuiltInRuleModules() {
    register(RiichiRuleConfig::class, "mahjongcraft:riichi") { config, id -> RiichiRuleModule(id, config) }
    register(TaiwanRuleConfig::class, "mahjongcraft:taiwan") { config, id -> TaiwanRuleModule(id, config) }
}
