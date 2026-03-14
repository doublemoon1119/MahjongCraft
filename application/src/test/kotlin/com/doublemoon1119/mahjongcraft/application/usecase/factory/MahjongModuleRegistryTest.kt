package com.doublemoon1119.mahjongcraft.application.usecase.factory

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.testing.fakes.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 測試用的規則配置實作 A。
 */
private class FakeConfigA(
    override val initialHandSize: Int = 13,
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
    override val deadTileCount: Int = 16,
    override val minimumWinConstraint: Int = 0,
    override val scoreConfig: FakeScoreConfig = FakeScoreConfig(),
    override val gameLength: FakeGameLength = FakeGameLength()
) : MahjongRuleConfig

/**
 * 測試用的通用模擬模組。
 */
private class FakeModule<T : MahjongRuleConfig>(
    override val id: String = "fake_module"
) : MahjongRuleModule<T> {
    override fun createWallFactory(config: T): TileWallFactory = object : TileWallFactory {
        override fun create() = throw UnsupportedOperationException()
    }

    override fun createDiscardPile(config: T) = FakeDiscardPile()

    override fun createShantenCalculator(config: T) = FakeShantenCalculator()

    override fun createLegalActionValidator(config: T): LegalActionValidator = FakeLegalActionValidator()
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
     * 驗證重複註冊相同 ID 的模組時，應拋出異常。
     */
    @Test
    fun `test duplicate registration throws exception`() {
        val registry = MahjongModuleRegistry()
        val firstModule = FakeModule<FakeConfigA>("moduleA")
        val secondModule = FakeModule<FakeConfigA>("moduleA") // Same ID

        registry.register(FakeConfigA::class, firstModule)

        assertFailsWith<IllegalArgumentException> {
            registry.register(FakeConfigA::class, secondModule)
        }
    }

    /**
     * 驗證註冊表是否能同時正確處理多種不同的配置類別。
     */
    @Test
    fun `test multiple registrations`() {
        val registry = MahjongModuleRegistry()
        val moduleA = FakeModule<FakeConfigA>("moduleA")
        val moduleB = FakeModule<FakeConfigB>("moduleB")

        val configA = FakeConfigA()
        val configB = FakeConfigB()

        registry.register(FakeConfigA::class, moduleA)
        registry.register(FakeConfigB::class, moduleB)

        assertEquals(moduleA, registry.getModule(configA))
        assertEquals(moduleB, registry.getModule(configB))
    }
}
