package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import net.minecraft.text.Text
import org.koin.core.annotation.Single

/**
 * [AiStrategyDisplayNameRegistry] 的 1.20.1 Fabric Koin binding。
 *
 * 介面本身跨版本／loader 共用，定義在 `platform/minecraft/common`；實作與 Koin binding 放在這裡，
 * 比照 [com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher] 與
 * 其 Fabric 實作 `FabricPlayerFeedbackPublisher` 的分工方式。實際邏輯委派給
 * [AiStrategyDisplayNameRegistryImpl]；內建與第三方策略的註冊、凍結時機交給
 * [com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar]。
 */
@Single(binds = [AiStrategyDisplayNameRegistry::class])
class FabricAiStrategyDisplayNameRegistry : AiStrategyDisplayNameRegistry by AiStrategyDisplayNameRegistryImpl()

/** 解析 [strategyKey] 的顯示名稱文字；查不到對應翻譯時退回顯示原始 key。 */
fun AiStrategyDisplayNameRegistry.resolveDisplayText(strategyKey: String): Text = find(strategyKey)
    ?.let(Text::translatable)
    ?: Text.literal(strategyKey)
