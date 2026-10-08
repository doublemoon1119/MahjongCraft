package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationEffect
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.module.RonExclusions
import com.doublemoon1119.mahjongcraft.logic.module.WinValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證日麻規則查詢與正式結算一致，並只依公開資訊回答不能榮和的牌與寶牌。 */
class RiichiPositionRulesTest {
    private val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig())
    private val rules = module.createPositionRules()

    /** 和牌價值等於正式榮和結算的點數，加上場上可收下的立直棒與本場。 */
    @Test
    fun `win value matches the formal ron settlement plus the stick pot and combo bonus`() {
        val winner = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(Tile.Honor.North))
        val discarder = seat(Wind.EAST)
        val table = table(listOf(discarder, winner), riichiSticks = 2, comboCount = 2)
        val winningTile = FakeIdentifiedTileFactory.create(TWO_BAMBOO)

        val resolution = module.declareRon(table, winner, winningTile, discarderId = discarder.id)
        val pot = module.collectStickPot(table)?.second ?: 0
        val comboBonus = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = winner.id,
            discarderId = discarder.id,
            resolution = resolution,
        ).values.sum()
        val value = rules.winValue(view(table, winner), winner.hand, TWO_BAMBOO, isTsumo = false)

        assertEquals(600, comboBonus)
        assertEquals(WinValue.Points(resolution!!.totalGained + pot + comboBonus), value)
    }

    /** 自摸的和牌價值等於正式自摸結算的點數，加上其他每家支付的本場。 */
    @Test
    fun `win value matches the formal tsumo settlement plus the combo bonus`() {
        val winner = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner, seat(Wind.WEST), seat(Wind.NORTH)), comboCount = 1)
        val drawnWinner = winner.copy(hand = winner.hand.copy(lastDrawn = FakeIdentifiedTileFactory.create(TWO_BAMBOO)))

        val resolution = module.declareTsumo(table, drawnWinner)
        val comboBonus = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = winner.id,
            discarderId = null,
            resolution = resolution,
        ).values.sum()
        val value = rules.winValue(view(table, winner), winner.hand, TWO_BAMBOO, isTsumo = true)

        assertEquals(300, comboBonus)
        assertEquals(WinValue.Points(resolution!!.totalGained + comboBonus), value)
    }

    /** 沒有役的聽牌不能榮和，但門前自摸仍然成立。 */
    @Test
    fun `a wait without yaku can only win by self draw`() {
        val winner = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo(), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner))

        assertEquals(WinValue.NotWinnable, rules.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = false))
        assertIs<WinValue.Points>(rules.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = true))
    }

    /** 假設宣告立直時，沒有役的門前聽牌也能榮和，且打點包含自己的那支立直棒。 */
    @Test
    fun `assuming a riichi declaration makes a closed hand without yaku winnable`() {
        val winner = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo(), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner))

        val value = rules.winValue(
            view(table, winner),
            winner.hand,
            FIVE_BAMBOO,
            isTsumo = false,
            declarations = setOf(RIICHI_GAME_ACTION),
        )

        val points = assertIs<WinValue.Points>(value).points
        assertTrue(points > RIICHI_STICK_POINTS, "the win must be worth more than the returned stick")
    }

    /** 自己牌河中有聽牌時不能榮和，自摸不受影響。 */
    @Test
    fun `discard furiten blocks ron but not self draw`() {
        val winner = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(TWO_BAMBOO))
        val table = table(listOf(seat(Wind.EAST), winner))

        assertEquals(WinValue.NotWinnable, rules.winValue(view(table, winner), winner.hand, TWO_BAMBOO, isTsumo = false))
        assertIs<WinValue.Points>(rules.winValue(view(table, winner), winner.hand, TWO_BAMBOO, isTsumo = true))
    }

    /** 沒有完成和牌型的牌不能和牌。 */
    @Test
    fun `a tile that does not complete the hand is not winnable`() {
        val winner = seat(Wind.SOUTH, hand = yakuhaiTankiOnTwoBamboo(), discards = listOf(Tile.Honor.North))
        val table = table(listOf(seat(Wind.EAST), winner))

        assertEquals(WinValue.NotWinnable, rules.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = true))
    }

    /** 立直要付出一支立直棒，且宣告後不能再換牌；其他擴充動作沒有效果。 */
    @Test
    fun `riichi costs a stick and locks the hand`() {
        val self = seat(Wind.SOUTH)
        val view = view(table(listOf(seat(Wind.EAST), self)), self)

        assertEquals(
            DeclarationEffect(cost = RIICHI_STICK_POINTS, locksHand = true),
            rules.declarationEffect(view, RIICHI_GAME_ACTION),
        )
        assertEquals(DeclarationEffect.NONE, rules.declarationEffect(view, GameAction.Extension(OtherAction)))
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
            rules.winValue(view(plainTable, plain), plain.hand, TWO_BAMBOO, isTsumo = false),
        ).points
        val redPoints = assertIs<WinValue.Points>(
            rules.winValue(view(redTable, red), red.hand, TWO_BAMBOO, isTsumo = false),
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

        assertEquals(WinValue.NotWinnable, rules.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = false))
        assertIs<WinValue.Points>(rules.winValue(view(table, winner), winner.hand, FIVE_BAMBOO, isTsumo = true))
    }

    /** 尚未立直的門清手牌（含暗槓）聽牌後可以立直；有副露、已經立直或點數不足一支立直棒時不行。 */
    @Test
    fun `a closed hand that has not declared riichi can riichi once ready`() {
        val riichi = setOf(RIICHI_GAME_ACTION)
        val closedKan = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.White) },
            sourceDirection = RelativeDirection.Self,
        )
        val closed = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo()).copy(score = RIICHI_STICK_POINTS)
        val withClosedKan = seat(Wind.SOUTH, hand = handOf(listOf(character(2), character(3)), melds = listOf(closedKan))).copy(score = RIICHI_STICK_POINTS)
        val open = seat(Wind.SOUTH, hand = handOf(listOf(character(2), character(3)), melds = listOf(pon(dot(7))))).copy(score = RIICHI_STICK_POINTS)
        val declared = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo(), riichi = true).copy(score = RIICHI_STICK_POINTS)
        val broke = seat(Wind.SOUTH, hand = noYakuTankiOnFiveBamboo()).copy(score = RIICHI_STICK_POINTS - 1)

        fun prospective(player: MahjongPlayer) = rules.prospectiveDeclarations(view(table(listOf(seat(Wind.EAST), player)), player), player.hand)

        assertEquals(riichi, prospective(closed))
        assertEquals(riichi, prospective(withClosedKan))
        assertEquals(emptySet(), prospective(open))
        assertEquals(emptySet(), prospective(declared))
        assertEquals(emptySet(), prospective(broke))
    }

    /** 對手自己打過的牌不能榮和，赤五與普通五視為同一種牌；沒有立直時沒有放過的牌。 */
    @Test
    fun `an opponent cannot ron tiles from their own discards`() {
        val self = seat(Wind.SOUTH, discards = listOf(character(1), character(2), character(3)))
        val opponent = seat(Wind.EAST, discards = listOf(character(5), RED_FIVE_DOT))

        assertEquals(
            RonExclusions(ownDiscards = setOf(character(5), dot(5))),
            rules.ronExclusions(view(table(listOf(opponent, self)), self), opponent.id),
        )
    }

    /** 立直者宣告後，其他玩家牌河中位置一定在宣告之後的牌都是放過的牌。 */
    @Test
    fun `tiles discarded after a riichi declaration are passed`() {
        val self = seat(Wind.SOUTH, discards = listOf(character(1), character(2), character(3), character(4)))
        val declarer = riichiDeclarer(discardsBefore = listOf(Tile.Honor.North))

        val passed = rules.ronExclusions(view(table(listOf(declarer, self)), self), declarer.id).passedAfterDeclaration

        assertEquals(setOf(character(3), character(4)), passed)
    }

    /** 每一次鳴牌都讓宣告之後的位置往後退兩張，只保留一定在宣告之後打出的牌。 */
    @Test
    fun `each call pushes the passed boundary back by two`() {
        val caller = seat(
            Wind.WEST,
            melds = listOf(pon(dot(7)).copy(sourceTile = FakeIdentifiedTileFactory.create(dot(7)))),
        )
        val self = seat(Wind.SOUTH, discards = (1..5).map(::character))
        val declarer = riichiDeclarer(discardsBefore = listOf(Tile.Honor.North))

        val passed = rules.ronExclusions(view(table(listOf(declarer, self, caller)), self), declarer.id).passedAfterDeclaration

        assertEquals(setOf(character(5)), passed)
    }

    /** 已經立直的對手，宣告聽牌時打出的牌就是立直宣告牌；赤五正規化為普通五。 */
    @Test
    fun `the declaration tile of a riichi opponent is the tile discarded to declare`() {
        val self = seat(Wind.SOUTH)
        val declarer = riichiDeclarer(discardsBefore = listOf(Tile.Honor.North), declarationTile = RED_FIVE_DOT)

        assertEquals(dot(5), rules.declarationTile(view(table(listOf(declarer, self)), self), declarer.id))
    }

    /** 沒有立直的對手沒有宣告聽牌時打出的牌。 */
    @Test
    fun `an opponent without riichi has no declaration tile`() {
        val self = seat(Wind.SOUTH)
        val opponent = seat(Wind.EAST, discards = listOf(character(5), Tile.Honor.West))

        assertNull(rules.declarationTile(view(table(listOf(opponent, self)), self), opponent.id))
    }

    /** 寶牌指示牌的下一張每出現一張指示牌就算一張寶牌，赤五另外算一張。 */
    @Test
    fun `bonus tiles follow the visible indicators and red fives`() {
        val self = seat(Wind.SOUTH)
        val view = view(table(listOf(seat(Wind.EAST), self)), self, doraIndicators = listOf(character(1), character(1), character(9)))

        assertEquals(2, rules.bonusTileCount(view, character(2)))
        assertEquals(1, rules.bonusTileCount(view, character(1)))
        assertEquals(1, rules.bonusTileCount(view, RED_FIVE_DOT))
        assertEquals(0, rules.bonusTileCount(view, dot(5)))
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

    /** 東家在打出 [discardsBefore] 之後，以 [declarationTile] 宣告立直，宣告牌之後沒有再打牌。 */
    private fun riichiDeclarer(discardsBefore: List<Tile>, declarationTile: Tile = Tile.Honor.West): MahjongPlayer {
        val riichiTile = FakeIdentifiedTileFactory.create(declarationTile)
        val pile = discardsBefore
            .fold(RiichiDiscardPile()) { pile, tile -> pile.discardTile(FakeIdentifiedTileFactory.create(tile)) }
            .discard(RiichiDiscardEntry(riichiTile, isRiichi = true))
        return FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            discardPile = pile,
            playerRuleState = RiichiPlayerState(riichiTile = riichiTile),
        )
    }

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

    private fun table(
        players: List<MahjongPlayer>,
        riichiSticks: Int = 0,
        comboCount: Int = 0,
    ): TableState = FakeTableStateFactory.create(
        players = players,
        dealerPlayerId = players.first().id,
        config = RiichiRuleConfig(),
        comboCount = comboCount,
        dynamicRuleState = RiichiDynamicState(riichiStickCount = riichiSticks),
    )

    /** 以 [player] 為觀察者的視角；[doraIndicators] 不為空時，牌山只包含這些公開的寶牌指示牌。 */
    private fun view(
        table: TableState,
        player: MahjongPlayer,
        doraIndicators: List<Tile> = emptyList(),
    ): PositionView {
        val snapshot = table.toSnapshot(visibleHandPlayerIds = setOf(player.id), setAsideTiles = { emptyList() })
        val withIndicators = if (doraIndicators.isEmpty()) {
            snapshot
        } else {
            snapshot.copy(tileWall = TileWallSnapshot(doraIndicators.map { IdentifiedTileSnapshot(id = Uuid.random(), tile = it) }))
        }
        return PositionView(snapshot = withIndicators, evaluatorId = player.id)
    }

    private fun character(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    private fun dot(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    private fun bamboo(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)

    private companion object {
        val TWO_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 2)
        val FIVE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 5)
        val RED_FIVE_DOT: Tile = RiichiTileTypes.redFive(Tile.Suit.Dot)
    }
}
