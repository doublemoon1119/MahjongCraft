// 檔名改為文字解析用途，保留既有頂層函式的 JVM 入口。
@file:JvmName("FabricAiStrategyDisplayNameRegistryKt")

package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import net.minecraft.text.Text

/**
 * 解析 [strategyKey] 的顯示名稱文字；查不到對應翻譯時退回顯示原始 key。
 *
 * @param strategyKey AI 策略註冊 key。
 * @return 可本地化的文字；找不到註冊名稱時為原始 key 文字。
 */
fun AiStrategyDisplayNameRegistry.resolveDisplayText(strategyKey: String): Text = find(strategyKey)
    ?.let(Text::translatable)
    ?: Text.literal(strategyKey)
