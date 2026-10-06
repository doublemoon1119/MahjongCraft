package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * [MahjongAiStrategyRegistryImpl] 的單元測試類別。
 */
class MahjongAiStrategyRegistryImplTest {
    /** 不登記額外動作的明確測試 registry。 */
    private val extensionActionRegistry = ExtensionGameActionAiRegistry(MahjongModuleRegistryImpl())

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

    /** 驗證 [MahjongAiStrategyRegistryImpl.getAllStrategyKeys] 依註冊順序列出所有 key。 */
    @Test
    fun `test getAllStrategyKeys lists keys in registration order`() {
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "b").apply {
            register("b") { RandomAiStrategy(extensionActionRegistry) }
            register("a") { RandomAiStrategy(extensionActionRegistry) }
        }

        assertEquals(listOf("b", "a"), registry.getAllStrategyKeys().toList())
    }

    /** 驗證同一個 key 不能註冊兩次，原本註冊的策略仍可解析。 */
    @Test
    fun `test register rejects a duplicate key`() {
        val original = RandomAiStrategy(extensionActionRegistry)
        val registry = MahjongAiStrategyRegistryImpl(defaultKey = "default").apply {
            register("custom") { original }
        }

        assertFailsWith<IllegalArgumentException> { registry.register("custom") { RandomAiStrategy(extensionActionRegistry) } }
        assertSame(original, registry.resolve("custom"))
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
}
