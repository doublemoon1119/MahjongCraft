package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證台灣麻將大三元、大四喜的包牌責任記錄與付款。 */
class TaiwanPaoLiabilityTest {
    /** 開啟包牌的規則模組。 */
    private val paoModule = TaiwanRuleModule(id = "mahjongcraft:taiwan", config = TaiwanRuleConfig(paoLiability = true))

    /** 預設不採用包牌的規則模組。 */
    private val defaultModule = TaiwanRuleModule(id = "mahjongcraft:taiwan", config = TaiwanRuleConfig())

    /** 已有兩組三元牌副露時，碰或明槓第三種三元牌記下打出者方位。 */
    @Test
    fun `claiming the third dragon meld records liability`() {
        val claimer = player(melds = listOf(pon(Tile.Honor.Red), pon(Tile.Honor.Green)), standing = List(3) { tile(Tile.Honor.White) })

        assertEquals(
            TaiwanPaoLiability(TaiwanPaoHand.BIG_DRAGONS, RelativeDirection.Left),
            liabilityAfterClaim(paoModule, claimer, MeldType.Pon, Tile.Honor.White, RelativeDirection.Left),
        )
        assertEquals(
            TaiwanPaoLiability(TaiwanPaoHand.BIG_DRAGONS, RelativeDirection.Across),
            liabilityAfterClaim(paoModule, claimer, MeldType.OpenKan, Tile.Honor.White, RelativeDirection.Across),
        )
    }

    /** 已有三組風牌副露時，碰第四種風牌記下打出者方位。 */
    @Test
    fun `claiming the fourth wind meld records liability`() {
        val claimer = player(
            melds = listOf(pon(Tile.Honor.East), pon(Tile.Honor.South), pon(Tile.Honor.West)),
            standing = List(2) { tile(Tile.Honor.North) },
        )

        assertEquals(
            TaiwanPaoLiability(TaiwanPaoHand.BIG_WINDS, RelativeDirection.Right),
            liabilityAfterClaim(paoModule, claimer, MeldType.Pon, Tile.Honor.North, RelativeDirection.Right),
        )
    }

    /** 未開啟包牌、只有一組副露或手中暗刻都不構成包牌。 */
    @Test
    fun `no liability without the option or a completed meld set`() {
        val twoDragonMelds = player(melds = listOf(pon(Tile.Honor.Red), pon(Tile.Honor.Green)), standing = List(2) { tile(Tile.Honor.White) })
        val concealedDragons = player(melds = listOf(pon(Tile.Honor.Red)), standing = List(3) { tile(Tile.Honor.Green) } + List(2) { tile(Tile.Honor.White) })

        assertNull(liabilityAfterClaim(defaultModule, twoDragonMelds, MeldType.Pon, Tile.Honor.White, RelativeDirection.Left))
        assertNull(liabilityAfterClaim(paoModule, concealedDragons, MeldType.Pon, Tile.Honor.White, RelativeDirection.Left))
    }

    /** 以大三元自摸時包牌者付全部；放槍時與放槍者平分。 */
    @Test
    fun `big dragons win moves the payment to the liable player`() {
        val table = dragonsTable(liable = true)
        val winner = table.players[WINNER]
        val liablePlayer = table.players[LIABLE]
        val discarder = table.players[DISCARDER]

        val tsumo = checkNotNull(paoModule.declareTsumo(table, winner.copy(hand = winner.hand.copy(lastDrawn = winningTile()))))
        assertEquals(mapOf(liablePlayer.id to tsumo.totalGained), tsumo.paymentsByPlayerId)
        assertEquals(mapOf(liablePlayer.id to BuiltInPaymentReasonIds.PAO), tsumo.settlement.paymentReasonIdsByPlayerId)

        val ron = checkNotNull(paoModule.declareRon(table, winner, winningTile(), discarder.id))
        assertEquals(setOf(liablePlayer.id, discarder.id), ron.paymentsByPlayerId.keys)
        assertEquals(ron.totalGained, ron.paymentsByPlayerId.values.sum())
        assertTrue(ron.paymentsByPlayerId.getValue(liablePlayer.id) >= ron.paymentsByPlayerId.getValue(discarder.id))
    }

    /** 有包牌紀錄但胡牌沒有成立大三元時照一般方式付款。 */
    @Test
    fun `liability does not apply when the winning hand lacks the pao hand`() {
        val table = dragonsTable(liable = true, liabilityHand = TaiwanPaoHand.BIG_WINDS)
        val winner = table.players[WINNER]

        val tsumo = checkNotNull(paoModule.declareTsumo(table, winner.copy(hand = winner.hand.copy(lastDrawn = winningTile()))))

        assertEquals(table.players.filterNot { it.id == winner.id }.map { it.id }.toSet(), tsumo.paymentsByPlayerId.keys)
        assertEquals(emptyMap(), tsumo.settlement.paymentReasonIdsByPlayerId)
    }

    /**
     * 讓玩家碰或明槓一張字牌，取得之後的包牌責任。
     *
     * @param module 使用的規則模組。
     * @param claimer 鳴牌前的玩家。
     * @param meldType 鳴牌種類。
     * @param called 鳴取的牌。
     * @param direction 打出者的相對方位。
     * @return 鳴牌後的包牌責任；沒有時為 null。
     */
    private fun liabilityAfterClaim(
        module: TaiwanRuleModule,
        claimer: MahjongPlayer,
        meldType: MeldType,
        called: Tile,
        direction: RelativeDirection,
    ): TaiwanPaoLiability? = module.beforeDiscardClaimed(claimer, meldType, tile(called), direction)
        .applyTo(claimer.components, BuiltInComponentOwners.PLAYER)[TaiwanPlayerComponents.PAO_LIABILITY]

    /**
     * 建立南家副露中發白三組、單騎九索聽大三元的四人桌況；包牌者為北家。
     *
     * @param liable 南家是否帶有包牌責任。
     * @param liabilityHand 包牌責任的牌型。
     * @return 桌況。
     */
    private fun dragonsTable(
        liable: Boolean,
        liabilityHand: TaiwanPaoHand = TaiwanPaoHand.BIG_DRAGONS,
    ): TableState {
        val players = Wind.entries.mapIndexed { index, wind ->
            if (index == WINNER) {
                player(
                    wind = wind,
                    melds = listOf(pon(Tile.Honor.Red), pon(Tile.Honor.Green), pon(Tile.Honor.White)),
                    standing = (1..6).map { tile(Tile.Numeric(Tile.Suit.Character, it)) } + tile(Tile.Numeric(Tile.Suit.Bamboo, 9)),
                )
            } else {
                FakeMahjongPlayerFactory.create(wind, discardPile = TaiwanDiscardPile())
            }
        }
        val table = FakeTableStateFactory.create(
            players = players,
            config = TaiwanRuleConfig(paoLiability = true),
            tileWall = TileWall(List(LIVE_TILES) { tile(Tile.Numeric(Tile.Suit.Dot, 9)) }),
        )
        if (!liable) return table
        val winner = table.players[WINNER]
        val liability = TaiwanPaoLiability(liabilityHand, table.relativeDirectionOf(winner.id, table.players[LIABLE].id))
        val withLiability = winner.copy(components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PAO_LIABILITY, liability))
        return table.copy(players = table.players.map { if (it.id == winner.id) withLiability else it })
    }

    /**
     * 建立已打過一張牌的玩家，因此胡牌不是起手胡。
     *
     * @param wind 座位風。
     * @param melds 副露。
     * @param standing 立牌。
     * @return 玩家。
     */
    private fun player(
        wind: Wind = Wind.SOUTH,
        melds: List<Meld>,
        standing: List<IdentifiedTile>,
    ): MahjongPlayer = FakeMahjongPlayerFactory.create(
        wind,
        hand = Hand(tiles = standing, melds = melds),
        discardPile = TaiwanDiscardPile().discardTile(tile(Tile.Numeric(Tile.Suit.Dot, 1))),
    )

    /**
     * 建立別人打出的牌碰成的刻子。
     *
     * @param honor 字牌。
     * @return 副露。
     */
    private fun pon(honor: Tile.Honor): Meld = FakeMeldFactory.create(
        type = MeldType.Pon,
        tiles = List(3) { tile(honor) },
        sourceDirection = RelativeDirection.Left,
    )

    /** 南家的胡牌張九索。 */
    private fun winningTile(): IdentifiedTile = tile(Tile.Numeric(Tile.Suit.Bamboo, 9))

    /**
     * 建立牌張。
     *
     * @param tile 牌面。
     * @return 牌張。
     */
    private fun tile(tile: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(tile)

    private companion object {
        /** 胡牌的南家。 */
        const val WINNER = 1

        /** 包牌的北家。 */
        const val LIABLE = 3

        /** 放槍的西家。 */
        const val DISCARDER = 2

        /** 牌牆剩餘張數，確保不是海底。 */
        const val LIVE_TILES = 20
    }
}
