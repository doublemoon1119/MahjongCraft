package com.doublemoon1119.mahjongcraft.platform.minecraft.ai

import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/**
 * 註冊 MahjongCraft 內建 AI 策略對應的顯示名稱。
 *
 * 內建映射與第三方映射共用 [AiStrategyDisplayNameRegistry.register]，不具有覆寫或繞過重複 key 驗證
 * 的特權。
 */
fun AiStrategyDisplayNameRegistry.registerBuiltInAiStrategyDisplayNames() {
    register(RandomAiStrategy.KEY, MinecraftMessageKeys.AI_STRATEGY_RANDOM)
}
