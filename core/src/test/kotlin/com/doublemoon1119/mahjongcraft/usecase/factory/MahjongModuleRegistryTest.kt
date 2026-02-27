package com.doublemoon1119.mahjongcraft.usecase.factory

import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.model.table.DiscardPile
import com.doublemoon1119.mahjongcraft.model.table.TileWallFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 測試用的 ScoreConfig 實作。
 */
class RegistryTestScoreConfig(
    override val initialScore: Int = 25000,
    override val bustThreshold: Int? = 0
) : ScoreConfig

/**
 * 測試用的 GameLength 實作。
 */
class RegistryTestGameLength(
    override val totalRounds: Int = 4,
    override val name: String = "RegistryTest"
) : GameLength

/**
 * 測試用的規則配置實作 A。
 */
class FakeConfigA(
    override val initialHandSize: Int = 13,
    override val tileSet: List<Tile> = emptyList(),
    override val deadTileCount: Int = 14,
    override val scoreConfig: ScoreConfig = RegistryTestScoreConfig(),
    override val gameLength: GameLength = RegistryTestGameLength(),
    override val minimumWinConstraint: Int = 1
) : MahjongRuleConfig

/**
 * 測試用的規則模組實作。
 */
class FakeModule<T : MahjongRuleConfig> : MahjongRuleModule<T> {
    override fun createWallFactory(config: T): TileWallFactory {
        throw UnsupportedOperationException("Not needed for registry testing")
    }
    override fun createDiscardPile(config: T): DiscardPile<*> {
        throw UnsupportedOperationException("Not needed for registry testing")
    }
}

/**
 * MahjongModuleRegistry 的單元測試。
 */
class MahjongModuleRegistryTest {

    /**
     * 驗證成功註冊並取得正確模組的流程。
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
     * 驗證當請求未註冊的配置時，是否拋出正確訊息的 IllegalStateException。
     */
    @Test
    fun `test get unregistered module throws exception`() {
        val registry = MahjongModuleRegistry()
        val configA = FakeConfigA()

        val exception = assertFailsWith<IllegalStateException>("Should throw IllegalStateException for unregistered configuration.") {
            registry.getModule(configA)
        }

        val expectedMessage = "No MahjongRuleModule registered for configuration: FakeConfigA"
        assertEquals(expectedMessage, exception.message, "The exception message should match the expected English format.")
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
        assertEquals(secondModule, result, "The registry should return the latest registered module.")
    }
}