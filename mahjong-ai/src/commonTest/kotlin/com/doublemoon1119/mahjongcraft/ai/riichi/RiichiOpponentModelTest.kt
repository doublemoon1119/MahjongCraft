package com.doublemoon1119.mahjongcraft.ai.riichi

import com.doublemoon1119.mahjongcraft.ai.expectation.ReadingDepth
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
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
import kotlin.test.assertTrue

/** 驗證日麻對手模型只依公開資訊估計捨牌危險度與對手威脅。 */
class RiichiOpponentModelTest {
    private val model = RiichiOpponentModel(
        rules = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig()).createPositionRules(),
        readingDepth = ReadingDepth.BASIC,
    )

    /** 對手牌河裡的牌不可能讓他榮和。 */
    @Test
    fun `a tile in the opponent discards is safe`() {
        val self = seat(Wind.SOUTH)
        val opponent = seat(Wind.EAST, discards = listOf(character(5)))
        val table = table(listOf(opponent, self))

        assertEquals(0.0, model.discardDanger(view(table, self), opponent.id, character(5)))
    }

    /** 對手捨過四萬時，一萬的兩面聽牌因振聽排除，一萬因此比沒有筋時安全。 */
    @Test
    fun `a suji tile is safer than the same tile without suji`() {
        val self = seat(Wind.SOUTH)
        val withSuji = seat(Wind.EAST, discards = listOf(character(4)))
        val withoutSuji = seat(Wind.EAST, discards = listOf(Tile.Honor.North))

        val sujiDanger = model.discardDanger(view(table(listOf(withSuji, self)), self), withSuji.id, character(1))
        val plainDanger = model.discardDanger(view(table(listOf(withoutSuji, self)), self), withoutSuji.id, character(1))

        assertTrue(sujiDanger < plainDanger, "suji $sujiDanger must be safer than $plainDanger")
    }

    /** 八筒全部出現時，以八筒構成的聽牌型不存在，九筒因此比平常安全。 */
    @Test
    fun `a wall of visible tiles makes the tile beyond it safer`() {
        val opponent = seat(Wind.EAST, discards = listOf(Tile.Honor.North))
        val withWall = seat(Wind.SOUTH, hand = handOf(List(4) { dot(8) }))
        val withoutWall = seat(Wind.SOUTH)

        val wallDanger = model.discardDanger(view(table(listOf(opponent, withWall)), withWall), opponent.id, dot(9))
        val plainDanger = model.discardDanger(view(table(listOf(opponent, withoutWall)), withoutWall), opponent.id, dot(9))

        assertTrue(wallDanger < plainDanger, "wall $wallDanger must be safer than $plainDanger")
    }

    /** 字牌只可能構成雙碰與單騎，比中張安全。 */
    @Test
    fun `an honor tile is safer than a middle tile`() {
        val self = seat(Wind.SOUTH)
        val opponent = seat(Wind.EAST)
        val view = view(table(listOf(opponent, self)), self)

        assertTrue(model.discardDanger(view, opponent.id, Tile.Honor.Green) < model.discardDanger(view, opponent.id, character(5)))
    }

    /** 立直者視為確定聽牌，打點依立直估計，莊家較高。 */
    @Test
    fun `a riichi declarer is certainly ready and the dealer is worth more`() {
        val self = seat(Wind.SOUTH)
        val dealer = seat(Wind.EAST, riichi = true)
        val nonDealer = seat(Wind.WEST, riichi = true)
        val view = view(table(listOf(dealer, self, nonDealer)), self)

        val dealerThreat = model.threat(view, dealer.id)
        val nonDealerThreat = model.threat(view, nonDealer.id)

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

        assertTrue(model.threat(view, open.id).readyProbability > model.threat(view, closed.id).readyProbability)
    }

    /** 赤五與普通五是同一種牌：對手捨過其中一種，另一種同樣是現物。 */
    @Test
    fun `a red five and a plain five are the same tile for safety`() {
        val self = seat(Wind.SOUTH)
        val discardedRed = seat(Wind.EAST, discards = listOf(RED_FIVE_DOT))
        val discardedPlain = seat(Wind.EAST, discards = listOf(dot(5)))

        assertEquals(0.0, model.discardDanger(view(table(listOf(discardedRed, self)), self), discardedRed.id, dot(5)))
        assertEquals(0.0, model.discardDanger(view(table(listOf(discardedPlain, self)), self), discardedPlain.id, RED_FIVE_DOT))
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

        val redValue = model.threat(view(table(listOf(dealer, self, withRed)), self), withRed.id).expectedWinValue
        val plainValue = model.threat(view(table(listOf(dealer, self, withoutRed)), self), withoutRed.id).expectedWinValue

        assertTrue(redValue > plainValue, "red five meld $redValue must be worth more than plain meld $plainValue")
    }

    /** 計算未見牌時赤五算作五：三張普通五加一張赤五與四張普通五同樣構成壁。 */
    @Test
    fun `a red five counts as a five when counting unseen tiles`() {
        val opponent = seat(Wind.EAST, discards = listOf(Tile.Honor.North))
        val withRed = seat(Wind.SOUTH, hand = handOf(List(3) { dot(5) } + RED_FIVE_DOT))
        val withPlain = seat(Wind.SOUTH, hand = handOf(List(4) { dot(5) }))

        val redDanger = model.discardDanger(view(table(listOf(opponent, withRed)), withRed), opponent.id, dot(6))
        val plainDanger = model.discardDanger(view(table(listOf(opponent, withPlain)), withPlain), opponent.id, dot(6))

        assertEquals(plainDanger, redDanger)
    }

    /** 剛摸到的牌只算一張可見牌：兩張八筒加摸到的八筒，與手中三張八筒的危險度相同。 */
    @Test
    fun `the drawn tile counts once when counting unseen tiles`() {
        val opponent = seat(Wind.EAST, discards = listOf(Tile.Honor.North))
        val withDrawn = seat(Wind.SOUTH, hand = handOf(List(2) { dot(8) }).copy(lastDrawn = FakeIdentifiedTileFactory.create(dot(8))))
        val withoutDrawn = seat(Wind.SOUTH, hand = handOf(List(3) { dot(8) }))

        val drawnDanger = model.discardDanger(view(table(listOf(opponent, withDrawn)), withDrawn), opponent.id, dot(9))
        val standingDanger = model.discardDanger(view(table(listOf(opponent, withoutDrawn)), withoutDrawn), opponent.id, dot(9))

        assertEquals(standingDanger, drawnDanger)
    }

    private fun handOf(tiles: List<Tile>): Hand = Hand(tiles = tiles.map(FakeIdentifiedTileFactory::create))

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

    private fun table(players: List<MahjongPlayer>): TableState = FakeTableStateFactory.create(
        players = players,
        dealerPlayerId = players.first().id,
        config = RiichiRuleConfig(),
        dynamicRuleState = RiichiDynamicState(),
    )

    private fun view(table: TableState, player: MahjongPlayer): PositionView = PositionView(snapshot = table.toSnapshot(visibleHandPlayerIds = setOf(player.id), setAsideTiles = { emptyList() }), evaluatorId = player.id)

    private fun character(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    private fun dot(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    private companion object {
        val RED_FIVE_DOT: Tile = RiichiTileTypes.redFive(Tile.Suit.Dot)
    }
}
