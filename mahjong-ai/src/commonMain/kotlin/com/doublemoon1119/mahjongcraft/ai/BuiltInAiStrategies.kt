package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectedValueAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.InformationLevel
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/** `:mahjong-ai` 內建期望值策略的登記 key；[RandomAiStrategy.KEY] 維持原本的 key 以相容既有存檔。 */
object BuiltInAiStrategyKeys {
    /** 初級：只看自己的手牌與向聽。 */
    const val BEGINNER: String = "mahjongcraft:beginner"

    /** 中級：數場上的牌、區分打點，並對高威脅的對手防守。 */
    const val INTERMEDIATE: String = "mahjongcraft:intermediate"

    /** 高級：使用全部資訊，包括後續風險與名次。 */
    const val ADVANCED: String = "mahjongcraft:advanced"
}

/**
 * 依初級、中級、高級、隨機出牌的順序註冊 `:mahjong-ai` 內建支援的策略；列出策略的畫面依此順序呈現。註冊方式與
 * 第三方策略相同，皆透過 [MahjongAiStrategyRegistry.register]，不具特權。
 *
 * @param moduleRegistry 期望值策略依對局設定取得規則模組。
 * @param extensionActionRegistry 將規則擴充動作轉成命令候選。
 */
fun MahjongAiStrategyRegistry.registerBuiltInAiStrategies(
    moduleRegistry: MahjongModuleRegistry,
    extensionActionRegistry: ExtensionGameActionAiRegistry,
) {
    mapOf(
        BuiltInAiStrategyKeys.BEGINNER to InformationLevel.BEGINNER,
        BuiltInAiStrategyKeys.INTERMEDIATE to InformationLevel.INTERMEDIATE,
        BuiltInAiStrategyKeys.ADVANCED to InformationLevel.ADVANCED,
    ).forEach { (key, level) ->
        register(key) {
            ExpectedValueAiStrategy(
                level = level,
                moduleRegistry = moduleRegistry,
                extensionActionRegistry = extensionActionRegistry,
            )
        }
    }
    register(RandomAiStrategy.KEY) { RandomAiStrategy(extensionActionRegistry = extensionActionRegistry) }
}
