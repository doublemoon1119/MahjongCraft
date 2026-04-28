package com.doublemoon1119.mahjongcraft.domain.module

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.fakes.config.FakeGameLength
import com.doublemoon1119.mahjongcraft.domain.fakes.config.FakeScoreConfig
import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory
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
    override val id: String,
    override val config: T
) : MahjongRuleModule<T> {

    // 對於確定不會用到的功能，統一使用此私有輔助方法拋出異常
    private fun notRequired(): Nothing = throw UnsupportedOperationException("Functional component not required for registry testing.")

    override fun createWallFactory(): TileWallFactory = notRequired()
    override fun createDiscardPile(): DiscardPile<*> = notRequired()
    override fun createShantenCalculator(): ShantenCalculator = notRequired()
    override fun createLegalActionValidator(): LegalActionValidator = notRequired()
    override fun createHandValueCalculator(): HandValueCalculator<*, *> = notRequired()
    override fun createHandValueContextCalculator(): HandValueContextCalculator<*, *> = notRequired()
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
        val registry: MahjongModuleRegistry = MahjongModuleRegistryImpl()
        val configA = FakeConfigA()
        val expectedId = "mahjongcraft:test"

        registry.register(FakeConfigA::class.java, expectedId) { config, id ->
            FakeModule(config = config, id = id)
        }

        val result = registry.getModule(configA)
        // 驗證 ID 是否正確傳遞
        assertEquals(expectedId, result.id, "The module should hold the ID specified during registration in the Registry.")
        assertEquals(configA, result.config)
    }

    /**
     * 驗證當請求未註冊的配置時，是否拋出預期的 IllegalStateException。
     */
    @Test
    fun `test get unregistered module throws exception`() {
        val registry: MahjongModuleRegistry = MahjongModuleRegistryImpl()
        val configA = FakeConfigA()

        val exception = assertFailsWith<IllegalStateException> {
            registry.getModule(configA)
        }

        val expectedMessage = "No registration for FakeConfigA"
        assertEquals(expectedMessage, exception.message)
    }

    /**
     * 驗證重複註冊相同 ID 的模組時，應拋出異常。
     */
    @Test
    fun `test duplicate registration throws exception`() {
        val registry: MahjongModuleRegistry = MahjongModuleRegistryImpl()
        val duplicateId = "mahjongcraft:duplicate"

        // 第一次註冊模組工廠
        registry.register(FakeConfigA::class.java, duplicateId) { config, id ->
            FakeModule(id = id, config = config)
        }

        // 驗證使用相同 ID 進行第二次註冊時，是否拋出 IllegalArgumentException
        assertFailsWith<IllegalArgumentException>(
            message = "Registry should throw IllegalArgumentException when registering a duplicate module ID."
        ) {
            registry.register(FakeConfigB::class.java, duplicateId) { config, id ->
                FakeModule(id = id, config = config)
            }
        }
    }

    /**
     * 驗證註冊表是否能同時正確處理多種不同的配置類別。
     */
    @Test
    fun `test multiple registrations`() {
        val registry: MahjongModuleRegistry = MahjongModuleRegistryImpl()
        val idA = "mahjongcraft:module_a"
        val idB = "mahjongcraft:module_b"

        // 註冊 A 配置及其對應工廠
        registry.register(FakeConfigA::class.java, idA) { config, id ->
            FakeModule(id = id, config = config)
        }

        // 註冊 B 配置及其對應工廠
        registry.register(FakeConfigB::class.java, idB) { config, id ->
            FakeModule(id = id, config = config)
        }

        val configA = FakeConfigA()
        val configB = FakeConfigB()

        // 獲取模組實體
        val resultA = registry.getModule(configA)
        val resultB = registry.getModule(configB)

        // 驗證回傳實體之 ID 與配置是否符合註冊時的定義
        assertEquals(
            expected = idA,
            actual = resultA.id,
            message = "Module A should retain the ID specified during registration."
        )
        assertEquals(
            expected = configA,
            actual = resultA.config,
            message = "Module A should hold the corresponding configuration instance."
        )

        assertEquals(
            expected = idB,
            actual = resultB.id,
            message = "Module B should retain the ID specified during registration."
        )
        assertEquals(
            expected = configB,
            actual = resultB.config,
            message = "Module B should hold the corresponding configuration instance."
        )
    }
}
