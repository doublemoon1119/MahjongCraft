package com.doublemoon1119.mahjongcraft.platform.fabric.server.rule

import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import net.minecraft.text.Text
import org.koin.core.annotation.Single

/**
 * [RuleModuleDisplayNameRegistry] 的 1.20.1 Fabric Koin binding。
 *
 * 介面本身跨版本／loader 共用，定義在 `platform/minecraft/common`；實作與 Koin binding 放在這裡，
 * 比照 `AiStrategyDisplayNameRegistry` 與其 Fabric 實作 `FabricAiStrategyDisplayNameRegistry` 的分工
 * 方式。實際邏輯委派給 [RuleModuleDisplayNameRegistryImpl]；內建與第三方規則模組的註冊、凍結時機
 * 交給 [MinecraftMahjongExtensionRegistrar]。
 *
 * 目前沒有任何指令／訊息實際消費這個 registry，先把第三方註冊入口鋪好；之後要顯示規則名稱的地方
 * 直接注入 [RuleModuleDisplayNameRegistry] 並呼叫 [resolveDisplayText]。
 */
@Single(binds = [RuleModuleDisplayNameRegistry::class])
class FabricRuleModuleDisplayNameRegistry : RuleModuleDisplayNameRegistry by RuleModuleDisplayNameRegistryImpl()

/** 解析 [ruleModuleId] 的顯示名稱文字；查不到對應翻譯時退回顯示原始 id。 */
fun RuleModuleDisplayNameRegistry.resolveDisplayText(ruleModuleId: String): Text = find(ruleModuleId)
    ?.let(Text::translatable)
    ?: Text.literal(ruleModuleId)
