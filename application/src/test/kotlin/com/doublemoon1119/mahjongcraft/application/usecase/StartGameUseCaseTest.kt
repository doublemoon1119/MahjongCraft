package com.doublemoon1119.mahjongcraft.application.usecase

import com.doublemoon1119.mahjongcraft.application.ports.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.application.usecase.factory.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.factory.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 針對 [StartGameUseCase] 進行的單元測試。
 *
 * 驗證遊戲初始化流程，包含玩家註冊、牌山生成及初始發牌邏輯。
 */
class StartGameUseCaseTest {
    private val dispatchers = TestCoroutineDispatchers()

    /**
     * 驗證在正常配置下，遊戲是否能正確初始化所有玩家狀態與手牌。
     */
    @Test
    fun `test start game successfully`() = runTest {
        // Arrange
        val registry = MahjongModuleRegistry()
        val config = FakeMahjongRuleConfig(initialHandSize = 13)


        // 註冊模擬規則模組
        registry.register(FakeMahjongRuleConfig::class, object : MahjongRuleModule<FakeMahjongRuleConfig> {
            override val id: String = "fake:module"
            override fun createWallFactory(config: FakeMahjongRuleConfig) = object : TileWallFactory {
                override fun create(): TileWall {
                    // 生成足夠數量的測試牌
                    val tiles = MutableList(100) { IdentifiedTile(UUID.randomUUID(), Tile.Honor.East) }
                    return TileWall(tiles)
                }
            }

            override fun createDiscardPile(config: FakeMahjongRuleConfig) = FakeDiscardPile()
        })

        val useCase = StartGameUseCase(registry, dispatchers)
        val playerMap = mapOf(
            UUID.randomUUID() to "Player1",
            UUID.randomUUID() to "Player2",
            UUID.randomUUID() to "Player3",
            UUID.randomUUID() to "Player4"
        )
        val request = StartGameRequest(playerMap, config)

        // Act
        val tableState = useCase(request)

        // Assert
        assertEquals(4, tableState.players.size)
        tableState.players.forEach { player ->
            // 驗證初始手牌張數是否符合設定（13張）
            assertEquals(13, player.hand.standingTiles.size)
            // 驗證初始分數是否正確設定
            assertEquals(config.scoreConfig.initialScore, player.score)
        }
    }

    /**
     * 驗證當傳入未註冊模組的規則配置時，應拋出 IllegalStateException。
     */
    @Test
    fun `test start game with unregistered config should throw exception`() = runTest {
        // Arrange
        val registry = MahjongModuleRegistry() // 空註冊表
        val useCase = StartGameUseCase(registry, dispatchers)
        val request = StartGameRequest(emptyMap(), FakeMahjongRuleConfig())

        // Act & Assert
        val exception = assertFailsWith<IllegalStateException> {
            useCase(request)
        }
        assertEquals("No MahjongRuleModule registered for configuration: FakeMahjongRuleConfig", exception.message)
    }

    /**
     * 驗證當牌山剩餘牌數不足以發放初始手牌時的異常處理。
     */
    @Test
    fun `test tile wall exhausted during initial dealing`() = runTest {
        // Arrange
        val registry = MahjongModuleRegistry()
        val config = FakeMahjongRuleConfig(initialHandSize = 13)

        registry.register(FakeMahjongRuleConfig::class, object : MahjongRuleModule<FakeMahjongRuleConfig> {
            override val id: String = "fake:module"
            override fun createWallFactory(config: FakeMahjongRuleConfig) = object : TileWallFactory {
                override fun create(): TileWall {
                    // 故意提供不足發給 4 個玩家的牌數 (只有 10 張，需求為 13 * 4 = 52 張)
                    val tiles = MutableList(10) { IdentifiedTile(UUID.randomUUID(), Tile.Honor.East) }
                    return TileWall(tiles)
                }
            }

            override fun createDiscardPile(config: FakeMahjongRuleConfig) = FakeDiscardPile()
        })

        val useCase = StartGameUseCase(registry, dispatchers)
        val playerMap = mapOf(
            UUID.randomUUID() to "P1", UUID.randomUUID() to "P2",
            UUID.randomUUID() to "P3", UUID.randomUUID() to "P4"
        )
        val request = StartGameRequest(playerMap, config)

        // Act & Assert
        assertFailsWith<IllegalStateException> {
            useCase(request)
        }
    }
}
