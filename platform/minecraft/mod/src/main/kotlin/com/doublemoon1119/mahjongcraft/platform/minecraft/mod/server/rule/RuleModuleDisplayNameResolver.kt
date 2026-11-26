package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.rule

import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import net.minecraft.network.chat.Component

/**
 * 解析 [ruleModuleId] 的顯示名稱文字。查不到對應翻譯時退回顯示原始 id。
 *
 * @param ruleModuleId 規則模組 ID。
 * @return 可本地化的顯示名稱，缺少註冊時為原始 ID 文字。
 */
fun RuleModuleDisplayNameRegistry.resolveDisplayText(ruleModuleId: String): Component = find(ruleModuleId)
    ?.let(Component::translatable)
    ?: Component.literal(ruleModuleId)
