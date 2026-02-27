package com.doublemoon1119.mahjongcraft.usecase

import com.doublemoon1119.mahjongcraft.model.base.Hand
import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.model.table.TableState
import com.doublemoon1119.mahjongcraft.model.table.TileWall
import com.doublemoon1119.mahjongcraft.model.table.Wind
import com.doublemoon1119.mahjongcraft.test.fakes.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.test.fakes.FakeMahjongRuleConfig
import java.util.*
import kotlin.test.*

/**
 * 針對 [DrawTileUseCase] 進行的領域邏輯單元測試。
 *
 * 本測試使用自定義的 Fake 類別來模擬規則配置與牌河實體。
 */
class DrawTileUseCaseTest {

    private lateinit var useCase: DrawTileUseCase
    private val playerId: UUID = UUID.randomUUID()

    @BeforeTest
    fun `setup draw tile use case test environment`() {
        useCase = DrawTileUseCase()
    }

    /**
     * 測試在牌山有牌的情況下，摸牌動作是否能正確更新玩家的最後摸牌欄位。
     */
    @Test
    fun `test draw tile successfully`() {
        // 準備測試數據：一張數牌與包含該牌的牌山
        val targetTile = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
        val tileWall = TileWall(mutableListOf(targetTile))

        // 建立測試玩家，使用 FakeDiscardPile 替代匿名實作
        val player = MahjongPlayer(
            id = playerId,
            name = "TestPlayer",
            initialSeat = Wind.EAST,
            hand = Hand(),
            discardPile = FakeDiscardPile()
        )

        // 建立桌況狀態，使用 FakeMahjongRuleConfig 替代匿名實作
        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeMahjongRuleConfig()
        )

        // 執行摸牌行為
        useCase(tableState, playerId)

        // 驗證玩家最後摸到的牌（lastDrawn）是否為牌山中的那張牌
        assertNotNull(player.hand.lastDrawn)
        assertEquals(targetTile.id, player.hand.lastDrawn?.id)

        // 驗證牌山中的牌已被移除
        assertEquals(0, tileWall.remainingCount)
    }

    /**
     * 測試當牌山已空時，執行摸牌應拋出 IllegalStateException。
     */
    @Test
    fun `test draw tile when wall is empty should throw exception`() {
        // 準備空牌山
        val tileWall = TileWall(mutableListOf())
        val player = MahjongPlayer(
            id = playerId,
            name = "TestPlayer",
            initialSeat = Wind.EAST,
            hand = Hand(),
            discardPile = FakeDiscardPile()
        )

        val tableState = TableState(
            players = listOf(player),
            tileWall = tileWall,
            config = FakeMahjongRuleConfig()
        )

        // 驗證當呼叫 UseCase 時會觸發預期的異常
        assertFailsWith<IllegalStateException> {
            useCase(tableState, playerId)
        }
    }
}