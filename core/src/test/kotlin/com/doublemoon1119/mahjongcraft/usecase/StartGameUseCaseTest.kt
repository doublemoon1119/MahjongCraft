package com.doublemoon1119.mahjongcraft.usecase

import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.model.table.DiscardPile
import com.doublemoon1119.mahjongcraft.model.table.TileWall
import com.doublemoon1119.mahjongcraft.model.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.usecase.factory.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.usecase.factory.MahjongRuleModule
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 測試用的 ScoreConfig 實作。
 */
class FakeScoreConfig(
    override val initialScore: Int = 25000,
    override val bustThreshold: Int? = 0
) : ScoreConfig

/**
 * 測試用的 GameLength 實作。
 */
class FakeGameLength(
    override val totalRounds: Int = 4,
    override val name: String = "TestMode"
) : GameLength

/**
 * 測試用的規則配置實作，嚴格遵循 MahjongRuleConfig 介面。
 */
class FakeRuleConfig(
    override val initialHandSize: Int = 13,
    override val tileSet: List<Tile> = emptyList(),
    override val deadTileCount: Int = 14,
    override val scoreConfig: ScoreConfig = FakeScoreConfig(),
    override val gameLength: GameLength = FakeGameLength(),
    override val minimumWinConstraint: Int = 1
) : MahjongRuleConfig

/**
 * 測試用的基礎牌河實作，繼承 DiscardPile.DiscardEntry。
 */
class FakeDiscardPile : DiscardPile<DiscardPile.DiscardEntry> {
    private val _entries = mutableListOf<DiscardPile.DiscardEntry>()
    override val entries: List<DiscardPile.DiscardEntry> get() = _entries

    override fun discard(entry: DiscardPile.DiscardEntry) {
        _entries.add(entry)
    }

    override fun takeLast() {
        _entries.lastOrNull()?.isTaken = true
    }
}

/**
 * 開始遊戲使用案例的單元測試組。
 * 僅使用 kotlin.test 且不使用任何 Mock 框架。
 */
class StartGameUseCaseTest {

    /**
     * 驗證開始遊戲時的初始化邏輯是否正確執行。
     */
    @Test
    fun `test start game success`() {
        // Arrange
        val registry = MahjongModuleRegistry()
        val config = FakeRuleConfig(initialHandSize = 13)
        val initialWallCount = 136

        // 建立測試用模組
        val fakeModule = object : MahjongRuleModule<FakeRuleConfig> {
            override fun createWallFactory(config: FakeRuleConfig) = object : TileWallFactory {
                override fun create(): TileWall {
                    val tiles = MutableList(initialWallCount) {
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East)
                    }
                    return TileWall(tiles)
                }
            }

            override fun createDiscardPile(config: FakeRuleConfig) = FakeDiscardPile()
        }

        registry.register(FakeRuleConfig::class, fakeModule)

        val useCase = StartGameUseCase(registry)
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
        assertEquals(4, tableState.players.size, "Player count should be 4.")

        // 驗證發牌後的張數 (13 * 4 = 52)
        val expectedRemaining = initialWallCount - (4 * 13)
        assertEquals(
            expectedRemaining,
            tableState.tileWall.remainingCount,
            "RemainingCount should be correctly calculated after dealing."
        )

        tableState.players.forEach { player ->
            assertEquals(13, player.hand.standingTiles.size, "Each player should have 13 tiles in hand.")
            assertEquals(25000, player.score, "Initial score should be correctly assigned from ScoreConfig.")
        }
    }

    /**
     * 驗證當對應規則模組未註冊時，是否拋出正確的英文異常訊息。
     */
    @Test
    fun `test module not found throws exception`() {
        // Arrange
        val registry = MahjongModuleRegistry()
        val useCase = StartGameUseCase(registry)
        val request = StartGameRequest(mapOf(UUID.randomUUID() to "Test"), FakeRuleConfig())

        // Act & Assert
        val exception =
            assertFailsWith<IllegalStateException>("Should throw IllegalStateException when module is missing.") {
                useCase(request)
            }
        assertEquals("No MahjongRuleModule registered for configuration: FakeRuleConfig", exception.message)
    }

    /**
     * 驗證當牌山剩餘牌數不足以發放初始手牌時的異常處理。
     */
    @Test
    fun `test tile wall exhausted during initial dealing`() {
        // Arrange
        val registry = MahjongModuleRegistry()
        val config = FakeRuleConfig(initialHandSize = 13)

        registry.register(FakeRuleConfig::class, object : MahjongRuleModule<FakeRuleConfig> {
            override fun createWallFactory(config: FakeRuleConfig) = object : TileWallFactory {
                override fun create(): TileWall {
                    // 故意提供不足發給 4 個玩家的牌數 (只有 10 張)
                    val tiles = MutableList(10) { IdentifiedTile(UUID.randomUUID(), Tile.Honor.East) }
                    return TileWall(tiles)
                }
            }

            override fun createDiscardPile(config: FakeRuleConfig) = FakeDiscardPile()
        })

        val useCase = StartGameUseCase(registry)
        val playerMap = mapOf(
            UUID.randomUUID() to "P1", UUID.randomUUID() to "P2",
            UUID.randomUUID() to "P3", UUID.randomUUID() to "P4"
        )
        val request = StartGameRequest(playerMap, config)

        // Act & Assert
        // 因為 StartGameUseCase 中 TileWall.draw() 可能回傳 null，且 Hand.addTile 接收非空值
        // 這裡會拋出 NullPointerException 或在此前被攔截。
        assertFailsWith<Exception>("Should fail when tile wall is exhausted during dealing.") {
            useCase(request)
        }
    }
}