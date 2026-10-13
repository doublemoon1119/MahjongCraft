// 檔名改為文字解析用途，保留既有頂層函式的 JVM 入口。
@file:JvmName("FabricRuleModuleDisplayNameRegistryKt")

package com.doublemoon1119.mahjongcraft.platform.fabric.server.rule

import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import net.minecraft.text.Text

/**
 * 解析 [ruleModuleId] 的顯示名稱文字；查不到對應翻譯時退回顯示原始 id。
 *
 * @param ruleModuleId 規則模組 ID。
 * @return 可本地化的顯示名稱，缺少註冊時為原始 ID 文字。
 */
fun RuleModuleDisplayNameRegistry.resolveDisplayText(ruleModuleId: String): Text = find(ruleModuleId)
    ?.let(Text::translatable)
    ?: Text.literal(ruleModuleId)
