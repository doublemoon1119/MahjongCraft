package com.doublemoon1119.mahjongcraft.ai

/**
 * 保存策略識別碼與建立函式；登記時不建立策略，每次解析時建立獨立實例。
 *
 * @property key 策略的 namespaced 識別碼。
 * @property factory 每次解析策略時建立獨立實例的工廠。
 */
data class MahjongAiStrategyDefinition(
    val key: String,
    val factory: () -> MahjongAiStrategy,
)
