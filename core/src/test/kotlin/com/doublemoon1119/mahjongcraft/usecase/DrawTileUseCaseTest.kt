package com.doublemoon1119.mahjongcraft.usecase

import com.doublemoon1119.mahjongcraft.model.base.Hand
import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.model.table.*
import java.util.*
import kotlin.test.*

/**
 * 針對 [DrawTileUseCase] 進行的領域邏輯單元測試。
 */
class DrawTileUseCaseTest {

    private lateinit var useCase: DrawTileUseCase
    private val playerId = UUID.randomUUID()

    @BeforeTest
    fun `setup draw tile use case test environment`() {
        useCase = DrawTileUseCase()
    }

    /**
     * 測試在牌山有牌的情況下，摸牌動作是否能正確更新玩家的最後摸牌欄位。
     */
    @Test
    fun `test draw tile successfully`() {
        // 準備測試用的牌
        val targetTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
        val tileWall = TileWall(mutableListOf(targetTile))

        // 建立測試玩家與捨牌河實體
        val player = MahjongPlayer(
            id = playerId,
            name = "TestPlayer",
            initialSeat = Wind.EAST,
            hand = Hand(),
            discardPile = createMockDiscardPile()
        )

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = createMockConfig()
        )

        // 執行摸牌
        useCase(tableState, playerId)

        // 驗證玩家最後摸到的牌是否正確
        assertNotNull(player.hand.lastDrawn)
        assertEquals(targetTile.id, player.hand.lastDrawn?.id)
        // 驗證牌山數量是否減少
        assertEquals(0, tileWall.remainingCount)
    }

    /**
     * 測試當牌山已空時，執行摸牌應拋出 IllegalStateException。
     */
    @Test
    fun `test draw tile when wall is empty should throw exception`() {
        val tileWall = TileWall(mutableListOf())
        val player = MahjongPlayer(
            id = playerId,
            name = "TestPlayer",
            initialSeat = Wind.EAST,
            hand = Hand(),
            discardPile = createMockDiscardPile()
        )

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = createMockConfig()
        )

        assertFailsWith<IllegalStateException> {
            useCase(tableState, playerId)
        }
    }

    /**
     * 輔助方法：建立測試用的最小化規則配置。
     */
    private fun createMockConfig() = object : MahjongRuleConfig {
        override val initialHandSize = 13
        override val tileSet = emptyList<Tile>()
        override val deadTileCount = 14
        override val minimumWinConstraint = 1
        override val scoreConfig = object : ScoreConfig {
            override val initialScore = 25000
            override val bustThreshold = 0
        }
        override val gameLength = object : GameLength {
            override val totalRounds = 4
            override val name = "East Only"
        }
    }

    /**
     * 輔助方法：建立測試用的簡易捨牌河。
     */
    private fun createMockDiscardPile() = object : DiscardPile<DiscardPile.DiscardEntry> {
        override val entries = mutableListOf<DiscardPile.DiscardEntry>()
        override fun discard(entry: DiscardPile.DiscardEntry) { entries.add(entry) }
        override fun takeLast() { entries.lastOrNull()?.isTaken = true }
    }
}