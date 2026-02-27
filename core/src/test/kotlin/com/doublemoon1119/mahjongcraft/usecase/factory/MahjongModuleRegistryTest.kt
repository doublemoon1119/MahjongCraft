package com.doublemoon1119.mahjongcraft.usecase.factory

import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.test.fakes.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.test.fakes.FakeGameLength
import com.doublemoon1119.mahjongcraft.test.fakes.FakeScoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 測試用的規則配置實作 A。
 */
private class FakeConfigA(
    override val initialHandSize: Int = 13,
    override val tileSet: List<Tile> = emptyList(),
    override val deadTileCount: Int = 14,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: FakeScoreConfig = FakeScoreConfig(),
    override val gameLength: FakeGameLength = FakeGameLength()
) : MahjongRuleConfig

/**
 * 測試用的規則配置實作 B。
 */
private class FakeConfigB(
    override val initialHandSize: Int = 16,
    override val tileSet: List<Tile> = emptyList(),
    override val deadTileCount: Int = 16,
    override val minimumWinConstraint: Int = 0,
    override val scoreConfig: FakeScoreConfig = FakeScoreConfig(),
    override val gameLength: FakeGameLength = FakeGameLength()
) : MahjongRuleConfig

/**
 * 測試用的通用模擬模組。
 */
private class FakeModule<T : MahjongRuleConfig> : MahjongRuleModule<T> {
    override fun createWallFactory(config: T): TileWallFactory = object : TileWallFactory {
        override fun create() = throw UnsupportedOperationException()
    }
    override fun createDiscardPile(config: T) = FakeDiscardPile()
}

/**
 * 針對 [MahjongModuleRegistry] 進行的單元測試。
 */
class MahjongModuleRegistryTest {

    /**
     * 驗證註冊後是否能正確取出對應的模組。
     */
    @Test
    fun `test register and get module`() {
        val registry = MahjongModuleRegistry()
        val moduleA = FakeModule<FakeConfigA>()
        val configA = FakeConfigA()

        registry.register(FakeConfigA::class, moduleA)

        val result = registry.getModule(configA)
        assertEquals(moduleA, result, "The registry should return the module associated with FakeConfigA.")
    }

    /**
     * 驗證當請求未註冊的配置時，是否拋出預期的 IllegalStateException。
     */
    @Test
    fun `test get unregistered module throws exception`() {
        val registry = MahjongModuleRegistry()
        val configA = FakeConfigA()

        val exception = assertFailsWith<IllegalStateException> {
            registry.getModule(configA)
        }

        val expectedMessage = "No MahjongRuleModule registered for configuration: FakeConfigA"
        assertEquals(expectedMessage, exception.message)
    }

    /**
     * 驗證重複註冊相同配置時，後者是否會覆蓋前者。
     */
    @Test
    fun `test overwrite registration`() {
        val registry = MahjongModuleRegistry()
        val firstModule = FakeModule<FakeConfigA>()
        val secondModule = FakeModule<FakeConfigA>()
        val configA = FakeConfigA()

        registry.register(FakeConfigA::class, firstModule)
        registry.register(FakeConfigA::class, secondModule)

        val result = registry.getModule(configA)
        assertEquals(secondModule, result, "The second registered module should overwrite the first one.")
    }

    /**
     * 驗證註冊表是否能同時正確處理多種不同的配置類別。
     */
    @Test
    fun `test multiple registrations`() {
        val registry = MahjongModuleRegistry()
        val moduleA = FakeModule<FakeConfigA>()
        val moduleB = FakeModule<FakeConfigB>()

        val configA = FakeConfigA()
        val configB = FakeConfigB()

        registry.register(FakeConfigA::class, moduleA)
        registry.register(FakeConfigB::class, moduleB)

        assertEquals(moduleA, registry.getModule(configA))
        assertEquals(moduleB, registry.getModule(configB))
    }
}