package com.doublemoon1119.mahjongcraft.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [MahjongAiStrategyRegistryImpl] 的單元測試類別。
 */
class MahjongAiStrategyRegistryImplTest {

    /**
     * 驗證 [MahjongAiStrategyRegistryImpl.register] 後，[MahjongAiStrategyRegistryImpl.resolve]
     * 能拿到對應的策略實例。
     */
    @Test
    fun `test resolve returns strategy registered for key`() {
        val strategy = RandomAiStrategy()
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
        val defaultStrategy = RandomAiStrategy()
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
        val defaultStrategy = RandomAiStrategy()
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "default").apply {
            register("default") { defaultStrategy }
        }

        assertSame(defaultStrategy, registry.resolve("unknown"))
    }

    /**
     * 驗證 [MahjongAiStrategyRegistryImpl.getAllStrategyKeys] 反映所有已註冊的 key。
     */
    @Test
    fun `test getAllStrategyKeys reflects registered keys`() {
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "a").apply {
            register("a") { RandomAiStrategy() }
            register("b") { RandomAiStrategy() }
        }

        assertEquals(setOf("a", "b"), registry.getAllStrategyKeys())
    }

    /**
     * 驗證 [registerBuiltInAiStrategies] 會註冊 [RandomAiStrategy.KEY]，且能被 resolve。
     */
    @Test
    fun `test registerBuiltInAiStrategies registers RandomAiStrategy`() {
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = RandomAiStrategy.KEY).apply {
            registerBuiltInAiStrategies()
        }

        assertTrue(registry.getAllStrategyKeys().contains(RandomAiStrategy.KEY))
        assertTrue(registry.resolve(RandomAiStrategy.KEY) is RandomAiStrategy)
    }
}
