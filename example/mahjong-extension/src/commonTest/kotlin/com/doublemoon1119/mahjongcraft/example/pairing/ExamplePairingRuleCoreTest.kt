package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** 覆蓋簡易配對規則的牌型、牌山與固定自摸結算核心行為。 */
class ExamplePairingRuleCoreTest {
    /** 四同牌可拆成兩對，不成對與非本規則牌面不可胡牌。 */
    @Test
    fun `four equal tiles complete while unsupported and unpaired hands do not`() {
        val calculator = ExamplePairingShantenCalculator()
        val one = Tile.Numeric(Tile.Suit.Character, 1)
        assertIs<ShantenResult.Complete>(calculator.calculate(Hand(tiles = List(4) { identified(one) })))
        assertIs<ShantenResult.NotTenpai>(
            calculator.calculate(Hand(tiles = (1..4).map { identified(Tile.Numeric(Tile.Suit.Character, it)) })),
        )
        assertIs<ShantenResult.NotTenpai>(calculator.calculate(Hand(tiles = List(4) { identified(Tile.Honor.East) })))
        assertIs<ShantenResult.Tenpai>(calculator.calculate(Hand(tiles = List(3) { identified(one) })))
    }

    /** 不接受超出範圍的付款、場長或不足以開局的牌山點數。 */
    @Test
    fun `configuration rejects unsupported payment and round limits`() {
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(paymentPerPlayer = 0) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(paymentPerPlayer = Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(roundCount = 0) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(roundCount = Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(wallTileValues = setOf(1)) }
        assertFailsWith<IllegalArgumentException> { ExamplePairingRuleConfig(wallTileValues = setOf(1, 9)) }
    }

    /** 倒置一萬應與普通一萬使用相同的配對語意。 */
    @Test
    fun `inverted one man joins the ordinary one man pair`() {
        val tiles = listOf(
            identified(Tile.Numeric(Tile.Suit.Character, 1)),
            identified(ExamplePairingTileType.invertedOneMan()),
            identified(Tile.Numeric(Tile.Suit.Character, 2)),
            identified(Tile.Numeric(Tile.Suit.Character, 2)),
        )

        assertIs<ShantenResult.Complete>(
            ExamplePairingShantenCalculator().calculate(Hand(tiles = tiles)),
        )
    }

    /** 預設八種牌面會產生完整牌山，玩家人數只影響牌牆面數。 */
    @Test
    fun `default wall size and wall side count follow player count`() {
        for ((playerCount, expectedSides) in listOf(2 to 2, 3 to 4, 4 to 4)) {
            val expectedTileCount = 32
            val tiles = ExamplePairingWallFactory(playerCount).create().getAllTiles()
            assertEquals(expectedTileCount, tiles.size)
            val layout = ExamplePairingWallLayout(playerCount)
            assertEquals(expectedSides, layout.sideArrangement(0).sideCount)
            if (playerCount == 2) {
                assertEquals(0, layout.sideArrangement(0).quarterTurnsOf(0))
                assertEquals(2, layout.sideArrangement(0).quarterTurnsOf(1))
            }
            val result = layout.resolve(tiles, WallOpening(0, 1))
            assertEquals(expectedTileCount, result.drawOrder.size)
            assertEquals(expectedTileCount, result.structure.size)
        }
    }

    /** 選取的牌面才會加入牌山，且倒置一萬只在選取一萬時出現。 */
    @Test
    fun `selected wall values control tile contents`() {
        val tiles = ExamplePairingWallFactory(2, setOf(3, 4)).create().getAllTiles()
        assertEquals(8, tiles.size)
        assertEquals(4, tiles.count { it.tile == Tile.Numeric(Tile.Suit.Character, 3) })
        assertEquals(4, tiles.count { it.tile == Tile.Numeric(Tile.Suit.Character, 4) })
        assertEquals(0, tiles.count { it.tile == ExamplePairingTileType.invertedOneMan() })
    }

    /** 四面牌牆可用奇數種類分配成相對兩面較長的牌牆。 */
    @Test
    fun `odd selected wall values distribute across four sides`() {
        val tiles = ExamplePairingWallFactory(4, (1..5).toSet()).create().getAllTiles()
        val layout = ExamplePairingWallLayout(4, 5)
        val result = layout.resolve(tiles, WallOpening(0, 1))
        assertEquals(20, result.structure.size)
        assertEquals(setOf(0, 1, 2, 3), result.structure.values.map { it.side }.toSet())
        assertEquals(20, result.drawOrder.map { it.id }.toSet().size)
    }

    /** 所有可用牌面數與玩家人數都能讓擲骰開門位置套用到牌牆。 */
    @Test
    fun `all valid selected counts resolve every wall side`() {
        for (playerCount in 2..4) {
            for (valueCount in playerCount..8) {
                val tiles = ExamplePairingWallFactory(playerCount, (1..valueCount).toSet()).create().getAllTiles()
                val layout = ExamplePairingWallLayout(playerCount, valueCount)
                for (side in 0 until layout.sideArrangement(0).sideCount) {
                    val result = layout.resolve(tiles, WallOpening(side, 1))
                    assertEquals(tiles.size, result.drawOrder.size)
                    assertEquals(tiles.size, result.structure.size)
                }
            }
        }
    }

    /** 選取牌面數量會限制可開始的玩家人數，避免牌山不足以完成起手。 */
    @Test
    fun `selected wall values limit player count`() {
        assertEquals(2, ExamplePairingRuleConfig(wallTileValues = setOf(1, 2)).maxPlayers)
        assertEquals(3, ExamplePairingRuleConfig(wallTileValues = setOf(1, 2, 3)).maxPlayers)
        assertEquals(4, ExamplePairingRuleConfig(wallTileValues = setOf(1, 2, 3, 4)).maxPlayers)
    }

    /** 自摸時應向每一位其他玩家收取固定點數。 */
    @Test
    fun `self draw settlement charges every other player`() {
        val winnerId = Uuid.random()
        val otherId = Uuid.random()
        val winner = MahjongPlayer(
            id = winnerId,
            initialSeatIndex = 0,
            hand = Hand(
                tiles = listOf(
                    identified(Tile.Numeric(Tile.Suit.Character, 1)),
                    identified(Tile.Numeric(Tile.Suit.Character, 1)),
                    identified(Tile.Numeric(Tile.Suit.Character, 2)),
                ),
                lastDrawn = identified(Tile.Numeric(Tile.Suit.Character, 2)),
            ),
            discardPile = ExamplePairingDiscardPile(),
            seatWind = Wind.EAST,
        )
        val other = MahjongPlayer(
            id = otherId,
            initialSeatIndex = 1,
            discardPile = ExamplePairingDiscardPile(),
            seatWind = Wind.SOUTH,
        )
        val table = TableState(
            id = Uuid.random(),
            players = listOf(winner, other),
            config = ExamplePairingRuleConfig(),
            tileWall = TileWall(),
            dealerPlayerId = winnerId,
        )

        val result = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, ExamplePairingRuleConfig())
            .declareTsumo(table, winner)

        assertNotNull(result)
        assertEquals(10, result.settlement.totalGained)
        assertEquals(10, result.settlement.paymentsByPlayerId[otherId])
    }

    /**
     * 建立具有獨立身分的測試牌。
     *
     * @param tile 測試牌面。
     * @return 身分不與其他測試牌重複的牌張。
     */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
