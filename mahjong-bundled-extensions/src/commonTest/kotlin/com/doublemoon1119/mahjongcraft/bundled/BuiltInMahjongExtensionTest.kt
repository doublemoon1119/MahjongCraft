package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectedValueAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.InformationLevel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證規則中立的內建整合登記的內容。 */
class BuiltInMahjongExtensionTest {
    /** 依初級、中級、高級、隨機出牌的順序登記 AI 策略，且各自解析成對應的資訊等級。 */
    @Test
    fun `registers the three expected value levels then random`() {
        val moduleRegistry = MahjongModuleRegistryImpl()
        val extension = BuiltInMahjongExtension(
            moduleRegistry = moduleRegistry,
            extensionActionRegistry = ExtensionGameActionAiRegistry(moduleRegistry),
            opponentModelRegistry = OpponentModelRegistry(),
        )
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = BuiltInAiStrategyKeys.BEGINNER).apply {
            extension.registerAiStrategies(this)
        }

        assertEquals(
            listOf(BuiltInAiStrategyKeys.BEGINNER, BuiltInAiStrategyKeys.INTERMEDIATE, BuiltInAiStrategyKeys.ADVANCED, RandomAiStrategy.KEY),
            registry.getAllStrategyKeys().toList(),
        )
        assertIs<RandomAiStrategy>(registry.resolve(RandomAiStrategy.KEY))
        assertEquals(InformationLevel.BEGINNER, assertIs<ExpectedValueAiStrategy>(registry.resolve(BuiltInAiStrategyKeys.BEGINNER)).level)
        assertEquals(InformationLevel.INTERMEDIATE, assertIs<ExpectedValueAiStrategy>(registry.resolve(BuiltInAiStrategyKeys.INTERMEDIATE)).level)
        assertEquals(InformationLevel.ADVANCED, assertIs<ExpectedValueAiStrategy>(registry.resolve(BuiltInAiStrategyKeys.ADVANCED)).level)
        assertEquals(InformationLevel.BEGINNER, assertIs<ExpectedValueAiStrategy>(registry.resolve("unknown")).level)
    }
}
