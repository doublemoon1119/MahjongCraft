package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectedValueAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.InformationLevel
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [MahjongAiStrategyRegistryImpl] 的單元測試類別。
 */
class MahjongAiStrategyRegistryImplTest {
    /** 不登記額外動作的明確測試 registry。 */
    private val extensionActionRegistry = ExtensionGameActionAiRegistry()

    /**
     * 驗證 [MahjongAiStrategyRegistryImpl.register] 後，[MahjongAiStrategyRegistryImpl.resolve]
     * 能拿到對應的策略實例。
     */
    @Test
    fun `test resolve returns strategy registered for key`() {
        val strategy = RandomAiStrategy(extensionActionRegistry)
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "default").apply {
            register("custom") { strategy }
        }

        assertSame(strategy, registry.resolve("custom"))
    }

    /**
     * 驗證 [MahjongAiStrategyRegistryImpl.resolve] 對 null key 優雅退回 [defaultKey] 對應的策略。
     */
    @Test
    fun `test resolve with null key falls back to default`() {
        val defaultStrategy = RandomAiStrategy(extensionActionRegistry)
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "default").apply {
            register("default") { defaultStrategy }
        }

        assertSame(defaultStrategy, registry.resolve(null))
    }

    /**
     * 驗證 [MahjongAiStrategyRegistryImpl.resolve] 對未知 key 優雅退回 [defaultKey] 對應的策略，
     * 而不是拋出例外——避免在對局進行中因為 key 對應的策略消失（例如來源 mod 被移除）而中斷遊戲。
     */
    @Test
    fun `test resolve with unknown key falls back to default`() {
        val defaultStrategy = RandomAiStrategy(extensionActionRegistry)
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "default").apply {
            register("default") { defaultStrategy }
        }

        assertSame(defaultStrategy, registry.resolve("unknown"))
    }

    /**
     * 驗證 [MahjongAiStrategyRegistryImpl.getAllStrategyKeys] 依第一次註冊的順序列出所有 key；重新註冊同一個 key 不改變順序。
     */
    @Test
    fun `test getAllStrategyKeys lists keys in registration order`() {
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "b").apply {
            register("b") { RandomAiStrategy(extensionActionRegistry) }
            register("a") { RandomAiStrategy(extensionActionRegistry) }
            register("b") { RandomAiStrategy(extensionActionRegistry) }
        }

        assertEquals(listOf("b", "a"), registry.getAllStrategyKeys().toList())
    }

    /** 驗證凍結後不能再註冊策略，已註冊的策略仍可解析。 */
    @Test
    fun `test register after freeze throws`() {
        val strategy = RandomAiStrategy(extensionActionRegistry)
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "default").apply {
            register("default") { strategy }
            freeze()
        }

        assertFailsWith<IllegalStateException> { registry.register("late") { strategy } }
        assertSame(strategy, registry.resolve("default"))
    }

    /**
     * 驗證 [registerBuiltInAiStrategies] 依初級、中級、高級、隨機出牌的順序註冊，且各自解析成對應的資訊等級。
     */
    @Test
    fun `test registerBuiltInAiStrategies registers the three expected value levels then random`() {
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = BuiltInAiStrategyKeys.BEGINNER).apply {
            registerBuiltInAiStrategies(MahjongModuleRegistryImpl(), extensionActionRegistry)
        }

        assertEquals(
            listOf(BuiltInAiStrategyKeys.BEGINNER, BuiltInAiStrategyKeys.INTERMEDIATE, BuiltInAiStrategyKeys.ADVANCED, RandomAiStrategy.KEY),
            registry.getAllStrategyKeys().toList(),
        )
        assertTrue(registry.resolve(RandomAiStrategy.KEY) is RandomAiStrategy)
        assertEquals(InformationLevel.BEGINNER, assertIs<ExpectedValueAiStrategy>(registry.resolve(BuiltInAiStrategyKeys.BEGINNER)).level)
        assertEquals(InformationLevel.INTERMEDIATE, assertIs<ExpectedValueAiStrategy>(registry.resolve(BuiltInAiStrategyKeys.INTERMEDIATE)).level)
        assertEquals(InformationLevel.ADVANCED, assertIs<ExpectedValueAiStrategy>(registry.resolve(BuiltInAiStrategyKeys.ADVANCED)).level)
        assertEquals(InformationLevel.BEGINNER, assertIs<ExpectedValueAiStrategy>(registry.resolve("unknown")).level)
    }
}
