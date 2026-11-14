package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 台麻正式計台輸入的胡牌張邊界與花牌資料副本測試。 */
class TaiwanHandValueContextCalculatorTest {
    /** 胡牌張只傳一次，圈風與門風取自目前權威資料。 */
    @Test
    fun `context uses current winds and detaches the flower list`() {
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(players = listOf(player), prevalentWind = Wind.WEST)
        val flowers = mutableListOf<Tile>(Tile.Extension(TaiwanTileTypes.SPRING))
        val incoming = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val result = TaiwanHandValueContextCalculator().calculate(
            input = TaiwanHandValueContextCalculator.Input(
                tableState = table,
                player = table.players.single(),
                incomingTile = incoming,
                isTsumo = false,
                flowers = flowers,
                situation = TaiwanWinSituation(isRobbingKong = true),
            ),
        )
        flowers.clear()
        assertEquals(Wind.WEST, result.roundWind)
        assertEquals(Wind.SOUTH, result.seatWind)
        assertEquals(1, result.flowers.size)
        assertTrue(result.situation.isRobbingKong)
        assertEquals(Tile.Honor.Red, result.winningTile)
    }

    /** 重複傳入同一張胡牌張或非在座玩家時明確拒絕。 */
    @Test
    fun `duplicate winning tile and unknown player are rejected`() {
        val player = FakeMahjongPlayerFactory.create(hand = FakeHandFactory.create(lastDrawn = Tile.Honor.Red))
        val table = FakeTableStateFactory.create(players = listOf(player))
        val input = TaiwanHandValueContextCalculator.Input(
            tableState = table,
            player = player,
            incomingTile = player.hand.lastDrawn!!,
            isTsumo = true,
            flowers = emptyList(),
            situation = TaiwanWinSituation(),
        )
        assertFailsWith<IllegalArgumentException> { TaiwanHandValueContextCalculator().calculate(input) }
        assertFailsWith<IllegalArgumentException> {
            TaiwanHandValueContextCalculator().calculate(input.copy(player = FakeMahjongPlayerFactory.create()))
        }
    }
}
