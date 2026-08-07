package com.doublemoon1119.mahjongcraft.ai

/**
 * 註冊 `:mahjong-ai` 內建支援的策略。註冊方式與第三方策略相同，皆透過
 * [MahjongAiStrategyRegistry.register]，不具特權。
 */
fun MahjongAiStrategyRegistry.registerBuiltInAiStrategies() {
    register(RandomAiStrategy.KEY) { RandomAiStrategy() }
}
