package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationEffect
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.module.WinValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證日麻局面評估與正式結算一致，並只依公開資訊估計危險度與威脅。 */
class RiichiPositionEvaluatorTest {
    private val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig())
    private val evaluator = module.createPositionEvaluator()

    /** 和牌價值等於正式榮和結算的點數，加上場上可收下的立直棒。 */
    @Test
    fun `win value matches the formal ron settlement plus the stick pot`() {
        val winner = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(Tile.Honor.North))
        val discarder = seat(Wind.EAST)
        val table = table(listOf(discarder, winner), riichiSticks = 2)
        val winningTile = FakeIdentifiedTileFactory.create(TWO_BAMBOO)

        val settlement = module.declareRon(table, winner, winningTile, discarderId = discarder.id)?.settlement
        val pot = module.collectStickPot(table)?.second ?: 0
        val value = evaluator.winValue(view(table, winner), winner.hand, TWO_BAMBOO, isTsumo = false)

        assertEquals(WinValue.Points(settlement!!.totalGained + pot), value)
    }

    /** 沒有役的聽牌不能榮和，但門前自摸仍然成立。 */
    @Test
    fun `a wait without yaku can only win by self draw`() {
        val winner = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo(), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner))

        assertEquals(WinValue.NotWinnable, evaluator.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = false))
        assertIs<WinValue.Points>(evaluator.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = true))
    }

    /** 假設宣告立直時，沒有役的門前聽牌也能榮和，且打點包含自己的那支立直棒。 */
    @Test
    fun `assuming a riichi declaration makes a closed hand without yaku winnable`() {
        val winner = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo(), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner))

        val value = evaluator.winValue(
            view(table, winner),
            winner.hand,
            FIVE_BAMBOO,
            isTsumo = false,
            declarations = setOf(RIICHI_GAME_ACTION as GameAction.Extension),
        )

        val points = assertIs<WinValue.Points>(value).points
        assertTrue(points > RIICHI_STICK_POINTS, "the win must be worth more than the returned stick")
    }

    /** 自己牌河中有聽牌時不能榮和，自摸不受影響。 */
    @Test
    fun `discard furiten blocks ron but not self draw`() {
        val winner = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(TWO_BAMBOO))
        val table = table(listOf(seat(Wind.EAST), winner))

        assertEquals(WinValue.NotWinnable, evaluator.winValue(view(table, winner), winner.hand, TWO_BAMBOO, isTsumo = false))
        assertIs<WinValue.Points>(evaluator.winValue(view(table, winner), winner.hand, TWO_BAMBOO, isTsumo = true))
    }

    /** 沒有完成和牌型的牌不能和牌。 */
    @Test
    fun `a tile that does not complete the hand is not winnable`() {
        val winner = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner))

        assertEquals(WinValue.NotWinnable, evaluator.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = true))
    }

    /** 對手牌河裡的牌不可能讓他榮和。 */
    @Test
    fun `a tile in the opponent discards is safe`() {
        val self = seat(Wind.SOUTH)
        val opponent = seat(Wind.EAST, discards = listOf(character(5)))
        val table = table(listOf(opponent, self))

        assertEquals(0.0, evaluator.discardDanger(view(table, self), opponent.id, character(5)))
    }

    /** 對手捨過四萬時，一萬的兩面聽牌因振聽排除，一萬因此比沒有筋時安全。 */
    @Test
    fun `a suji tile is safer than the same tile without suji`() {
        val self = seat(Wind.SOUTH)
        val withSuji = seat(Wind.EAST, discards = listOf(character(4)))
        val withoutSuji = seat(Wind.EAST, discards = listOf(Tile.Honor.North))

        val sujiDanger = evaluator.discardDanger(view(table(listOf(withSuji, self)), self), withSuji.id, character(1))
        val plainDanger = evaluator.discardDanger(view(table(listOf(withoutSuji, self)), self), withoutSuji.id, character(1))

        assertTrue(sujiDanger < plainDanger, "suji $sujiDanger must be safer than $plainDanger")
    }

    /** 八筒全部出現時，以八筒構成的聽牌型不存在，九筒因此比平常安全。 */
    @Test
    fun `a wall of visible tiles makes the tile beyond it safer`() {
        val opponent = seat(Wind.EAST, discards = listOf(Tile.Honor.North))
        val withWall = seat(Wind.SOUTH, hand = handOf(List(4) { dot(8) }))
        val withoutWall = seat(Wind.SOUTH)

        val wallDanger = evaluator.discardDanger(view(table(listOf(opponent, withWall)), withWall), opponent.id, dot(9))
        val plainDanger = evaluator.discardDanger(view(table(listOf(opponent, withoutWall)), withoutWall), opponent.id, dot(9))

        assertTrue(wallDanger < plainDanger, "wall $wallDanger must be safer than $plainDanger")
    }

    /** 字牌只可能構成雙碰與單騎，比中張安全。 */
    @Test
    fun `an honor tile is safer than a middle tile`() {
        val self = seat(Wind.SOUTH)
        val opponent = seat(Wind.EAST)
        val view = view(table(listOf(opponent, self)), self)

        assertTrue(evaluator.discardDanger(view, opponent.id, Tile.Honor.Green) < evaluator.discardDanger(view, opponent.id, character(5)))
    }

    /** 立直者視為確定聽牌，打點依立直估計，莊家較高。 */
    @Test
    fun `a riichi declarer is certainly ready and the dealer is worth more`() {
        val self = seat(Wind.SOUTH)
        val dealer = seat(Wind.EAST, riichi = true)
        val nonDealer = seat(Wind.WEST, riichi = true)
        val view = view(table(listOf(dealer, self, nonDealer)), self)

        val dealerThreat = evaluator.threat(view, dealer.id)
        val nonDealerThreat = evaluator.threat(view, nonDealer.id)

        assertEquals(1.0, dealerThreat.readyProbability)
        assertEquals(1.0, nonDealerThreat.readyProbability)
        assertTrue(dealerThreat.expectedWinValue > nonDealerThreat.expectedWinValue)
    }

    /** 沒有立直時，副露越多越可能已經聽牌。 */
    @Test
    fun `open melds raise the ready probability`() {
        val self = seat(Wind.SOUTH)
        val closed = seat(Wind.EAST)
        val open = seat(Wind.WEST, melds = listOf(pon(character(7)), pon(dot(3))))
        val view = view(table(listOf(closed, self, open)), self)

        assertTrue(evaluator.threat(view, open.id).readyProbability > evaluator.threat(view, closed.id).readyProbability)
    }

    /** 立直要付出一支立直棒，且宣告後不能再換牌；其他擴充動作沒有效果。 */
    @Test
    fun `riichi costs a stick and locks the hand`() {
        val self = seat(Wind.SOUTH)
        val view = view(table(listOf(seat(Wind.EAST), self)), self)

        assertEquals(
            DeclarationEffect(cost = RIICHI_STICK_POINTS, locksHand = true),
            evaluator.declarationEffect(view, RIICHI_GAME_ACTION as GameAction.Extension),
        )
        assertEquals(DeclarationEffect.NONE, evaluator.declarationEffect(view, GameAction.Extension(OtherAction)))
    }

    /** 赤五筒多一番，打點比普通五筒高，而且與正式榮和結算一致。 */
    @Test
    fun `a red five adds value and still matches the formal settlement`() {
        val plain = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(Tile.Honor.North))
        val red = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(fiveDot = RED_FIVE_DOT), discards = listOf(Tile.Honor.North))
        val discarder = seat(Wind.EAST)
        val plainTable = table(listOf(discarder, plain))
        val redTable = table(listOf(discarder, red))

        val plainPoints = assertIs<WinValue.Points>(
            evaluator.winValue(view(plainTable, plain), plain.hand, TWO_BAMBOO, isTsumo = false),
        ).points
        val redPoints = assertIs<WinValue.Points>(
            evaluator.winValue(view(redTable, red), red.hand, TWO_BAMBOO, isTsumo = false),
        ).points
        val redSettlement = module.declareRon(
            redTable,
            red,
            FakeIdentifiedTileFactory.create(TWO_BAMBOO),
            discarderId = discarder.id,
        )?.settlement

        assertTrue(redPoints > plainPoints, "red five $redPoints must be worth more than plain five $plainPoints")
        assertEquals(redSettlement!!.totalGained, redPoints)
    }

    /** 赤寶牌只加打點、不算役：只有赤寶牌的門前聽牌仍然不能榮和，自摸則成立。 */
    @Test
    fun `a red five does not count toward the minimum win requirement`() {
        val winner = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo(fiveDot = RED_FIVE_DOT), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner))

        assertEquals(WinValue.NotWinnable, evaluator.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = false))
        assertIs<WinValue.Points>(evaluator.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = true))
    }

    /** 赤五與普通五是同一種牌：對手捨過其中一種，另一種同樣是現物。 */
    @Test
    fun `a red five and a plain five are the same tile for safety`() {
        val self = seat(Wind.SOUTH)
        val discardedRed = seat(Wind.EAST, discards = listOf(RED_FIVE_DOT))
        val discardedPlain = seat(Wind.EAST, discards = listOf(dot(5)))

        assertEquals(0.0, evaluator.discardDanger(view(table(listOf(discardedRed, self)), self), discardedRed.id, dot(5)))
        assertEquals(0.0, evaluator.discardDanger(view(table(listOf(discardedPlain, self)), self), discardedPlain.id, RED_FIVE_DOT))
    }

    /** 對手副露中的赤五會提高他的預估打點。 */
    @Test
    fun `a red five in an opponent meld raises the expected loss`() {
        val self = seat(Wind.SOUTH)
        val withRed = seat(
            Wind.WEST,
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(dot(5), dot(5), RED_FIVE_DOT).map(FakeIdentifiedTileFactory::create),
                    sourceDirection = RelativeDirection.Across,
                ),
            ),
        )
        val withoutRed = seat(Wind.WEST, melds = listOf(pon(dot(5))))
        val dealer = seat(Wind.EAST)

        val redValue = evaluator.threat(view(table(listOf(dealer, self, withRed)), self), withRed.id).expectedWinValue
        val plainValue = evaluator.threat(view(table(listOf(dealer, self, withoutRed)), self), withoutRed.id).expectedWinValue

        assertTrue(redValue > plainValue, "red five meld $redValue must be worth more than plain meld $plainValue")
    }

    /** 計算未見牌時赤五算作五：三張普通五加一張赤五與四張普通五同樣構成壁。 */
    @Test
    fun `a red five counts as a five when counting unseen tiles`() {
        val opponent = seat(Wind.EAST, discards = listOf(Tile.Honor.North))
        val withRed = seat(Wind.SOUTH, hand = handOf(List(3) { dot(5) } + RED_FIVE_DOT))
        val withPlain = seat(Wind.SOUTH, hand = handOf(List(4) { dot(5) }))

        val redDanger = evaluator.discardDanger(view(table(listOf(opponent, withRed)), withRed), opponent.id, dot(6))
        val plainDanger = evaluator.discardDanger(view(table(listOf(opponent, withPlain)), withPlain), opponent.id, dot(6))

        assertEquals(plainDanger, redDanger)
    }

    /** 測試用、不屬於日麻的擴充動作。 */
    private object OtherAction : ExtensionGameAction {
        override val id: String = "example:other"
    }

    /** 二三四萬、四五六筒、七八九筒、中中中、二條：單騎聽二條，中提供役牌役；[fiveDot] 可換成赤五筒。 */
    private fun yakuhaiTankiOnTwoBamboo(fiveDot: Tile = dot(5)): Hand = handOf(
        listOf(character(2), character(3), character(4), dot(4), fiveDot, dot(6), dot(7), dot(8), dot(9)) +
            List(3) { Tile.Honor.Red } + TWO_BAMBOO,
    )

    /** 二三四萬、四五六筒、七八九筒、二三四條、五條：單騎聽五條，門前榮和沒有役；[fiveDot] 可換成赤五筒。 */
    private fun noYakuTankiOnFiveBamboo(fiveDot: Tile = dot(5)): Hand = handOf(
        listOf(character(2), character(3), character(4), dot(4), fiveDot, dot(6), dot(7), dot(8), dot(9)) +
            listOf(bamboo(2), bamboo(3), bamboo(4), FIVE_BAMBOO),
    )

    private fun handOf(tiles: List<Tile>, melds: List<Meld> = emptyList()): Hand = Hand(tiles = tiles.map(FakeIdentifiedTileFactory::create), melds = melds)

    private fun pon(tile: Tile): Meld = Meld(
        type = MeldType.PON,
        tiles = List(3) { FakeIdentifiedTileFactory.create(tile) },
        sourceDirection = RelativeDirection.Across,
    )

    private fun seat(
        wind: Wind,
        hand: Hand = Hand(),
        discards: List<Tile> = emptyList(),
        melds: List<Meld> = emptyList(),
        riichi: Boolean = false,
    ): MahjongPlayer = FakeMahjongPlayerFactory.create(
        initialSeat = wind,
        hand = hand.copy(melds = hand.melds + melds),
        discardPile = discards.fold(RiichiDiscardPile()) { pile, tile -> pile.discardTile(FakeIdentifiedTileFactory.create(tile)) },
        playerRuleState = RiichiPlayerState(riichiTile = if (riichi) FakeIdentifiedTileFactory.create(Tile.Honor.West) else null),
    )

    private fun table(players: List<MahjongPlayer>, riichiSticks: Int = 0): TableState = FakeTableStateFactory.create(
        players = players,
        dealerPlayerId = players.first().id,
        config = RiichiRuleConfig(),
        dynamicRuleState = RiichiDynamicState(riichiStickCount = riichiSticks),
    )

    private fun view(table: TableState, player: MahjongPlayer): PositionView = PositionView(snapshot = table.toSnapshot(visibleHandPlayerIds = setOf(player.id)), evaluatorId = player.id)

    private fun character(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    private fun dot(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    private fun bamboo(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)

    private companion object {
        val TWO_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 2)
        val FIVE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 5)
        val RED_FIVE_DOT: Tile = RiichiTileTypes.redFive(Tile.Suit.Dot)
    }
}
