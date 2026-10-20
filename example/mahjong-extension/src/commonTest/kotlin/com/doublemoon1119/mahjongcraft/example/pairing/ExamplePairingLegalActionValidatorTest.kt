package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證摸牌放在手牌或單獨傳入時，範例規則提供相同的自摸資格。 */
class ExamplePairingLegalActionValidatorTest {
    /** 兩對與倒置一萬都能以兩種傳入方式取得相同的合法動作。 */
    @Test
    fun `separate incoming tile and stored draw offer the same self draw`() {
        for (firstTile in listOf(Tile.Numeric(Tile.Suit.Character, 1), ExamplePairingTileType.invertedOneMan())) {
            val hand = Hand(
                tiles = listOf(identified(firstTile), numeric(1), numeric(2)),
                lastDrawn = numeric(2),
            )
            val expected = actions(hand, null)
            assertTrue(GameAction.Tsumo in expected)
            assertEquals(expected, actions(hand.copy(lastDrawn = null), hand.lastDrawn))
            assertEquals(expected, actions(hand, hand.lastDrawn))
        }
    }

    /** 非完成牌型不會因為摸牌改成單獨傳入而取得自摸資格。 */
    @Test
    fun `separate incoming tile does not complete an unpaired hand`() {
        val hand = Hand(
            tiles = listOf(numeric(1), numeric(1), numeric(2)),
            lastDrawn = numeric(3),
        )
        val expected = actions(hand, null)
        assertFalse(GameAction.Tsumo in expected)
        assertEquals(expected, actions(hand.copy(lastDrawn = null), hand.lastDrawn))
    }

    /** 沒有摸牌時不提供自摸或摸牌後宣告。 */
    @Test
    fun `no draw offers no post draw actions`() {
        assertEquals(emptyList(), actions(Hand(tiles = listOf(numeric(1), numeric(1), numeric(2))), null))
    }

    /**
     * 使用指定手牌與單獨傳入的摸牌查詢合法動作。
     *
     * @param hand 判定時的手牌。
     * @param incomingTile 單獨提供的摸牌；null 時由手牌讀取。
     * @return 當前玩家的合法動作。
     */
    private fun actions(hand: Hand, incomingTile: IdentifiedTile?): List<GameAction> {
        val player = MahjongPlayer(
            id = Uuid.random(),
            initialSeatIndex = 0,
            hand = hand,
            discardPile = ExamplePairingDiscardPile(),
            playerRuleState = ExamplePairingPlayerState(),
            seatWind = Wind.EAST,
        )
        val table = TableState(
            id = Uuid.random(),
            players = listOf(player),
            config = ExamplePairingRuleConfig(),
            tileWall = TileWall(),
            dealerPlayerId = player.id,
        )
        return ExamplePairingLegalActionValidator().getLegalActions(
            tableState = table,
            player = player,
            sourceAction = GameAction.Draw,
            sourceDirection = RelativeDirection.Self,
            incomingTile = incomingTile,
        )
    }

    /**
     * 建立具有獨立身分的萬子牌。
     *
     * @param value 牌面數字。
     * @return 測試牌張。
     */
    private fun numeric(value: Int): IdentifiedTile = identified(Tile.Numeric(Tile.Suit.Character, value))

    /**
     * 建立具有獨立身分的牌張。
     *
     * @param tile 牌面。
     * @return 測試牌張。
     */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
