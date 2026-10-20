package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.example.action.ExamplePairingDeclarationAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證範例規則使用統一的合法動作判定資料。 */
class ExamplePairingLegalActionValidatorTest {
    /** 完成牌型且來源為自己的摸牌時，提供自摸與宣告。 */
    @Test
    fun `complete own turn offers tsumo and declaration`() {
        for (firstTile in listOf(Tile.Numeric(Tile.Suit.Character, 1), ExamplePairingTileType.invertedOneMan())) {
            val hand = Hand(
                tiles = listOf(identified(firstTile), numeric(1), numeric(2)),
                lastDrawn = numeric(2),
            )
            val actions = actions(hand)
            assertTrue(GameAction.Tsumo in actions)
            assertTrue(GameAction.Extension(ExamplePairingDeclarationAction) in actions)
        }
    }

    /** 尚未完成牌型時，不提供自摸。 */
    @Test
    fun `incomplete own turn does not offer tsumo`() {
        val hand = Hand(
            tiles = listOf(numeric(1), numeric(1), numeric(2)),
            lastDrawn = numeric(3),
        )
        assertTrue(GameAction.Tsumo !in actions(hand))
    }

    /** 沒有摸牌時不提供自摸或摸牌後宣告。 */
    @Test
    fun `no draw offers no post draw actions`() {
        assertEquals(emptyList(), actions(Hand(tiles = listOf(numeric(1), numeric(1), numeric(2)))))
    }

    /**
     * 使用指定手牌查詢合法動作。
     *
     * @param hand 判定時的手牌。
     * @return 當前玩家的合法動作。
     */
    private fun actions(hand: Hand): List<GameAction> {
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
            LegalActionContext.ownTurn(table, player.id),
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
