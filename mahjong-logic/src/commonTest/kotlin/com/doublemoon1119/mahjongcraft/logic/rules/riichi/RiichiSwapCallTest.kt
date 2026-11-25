package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 日本麻將的食替限制：吃碰後不能打出的牌，以及吃碰後會無牌可打時不提供該吃碰。 */
class RiichiSwapCallTest {
    /** 四人日本麻將規則模組。 */
    private val module = RiichiRuleModule(id = BuiltInRuleModuleIds.RIICHI, config = RiichiRuleConfig())

    /** 三人日本麻將規則模組。 */
    private val threePlayerModule = ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, ThreePlayerRiichiRuleConfig())

    /** 依日麻規則判定合法動作的驗證器。 */
    private val validator = RiichiLegalActionValidator(
        shantenCalculator = RiichiShantenCalculator(),
        handValueCalculator = RiichiHandValueCalculator(),
        contextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig()),
    )

    /** 用兩頭吃進順子一端的牌後，同一種牌與順子另一頭外側的牌都不能打。 */
    @Test
    fun `two-sided chi forbids the called tile and the other end`() {
        val sameKind = character(3)
        val otherEnd = character(6)
        val free = character(9)
        val player = afterChi(called = character(3), with = listOf(character(4), character(5)), standing = listOf(sameKind, otherEnd, free))

        assertEquals(setOf(free.id), discardable(player))
    }

    /** 吃進順子中間的牌（坎張）只限制同一種牌。 */
    @Test
    fun `middle chi forbids only the called tile`() {
        val sameKind = character(4)
        val below = character(2)
        val above = character(6)
        val player = afterChi(called = character(4), with = listOf(character(3), character(5)), standing = listOf(sameKind, below, above))

        assertEquals(setOf(below.id, above.id), discardable(player))
    }

    /** 邊張吃進三萬時，另一頭外側不存在，只限制同一種牌。吃進一萬時限制四萬。 */
    @Test
    fun `edge chi limits the other end to existing tiles`() {
        val three = character(3)
        val four = character(4)
        val penchan = afterChi(called = character(3), with = listOf(character(1), character(2)), standing = listOf(three, four))
        val lowEnd = afterChi(called = character(1), with = listOf(character(2), character(3)), standing = listOf(three, four, character(1)))

        assertEquals(setOf(four.id), discardable(penchan))
        assertEquals(setOf(three.id), discardable(lowEnd))
    }

    /** 碰後不能打出同一種牌，赤五與一般的五算同一種。 */
    @Test
    fun `pon forbids the same kind including red fives`() {
        val redFive = FakeIdentifiedTileFactory.create(RiichiTileValues.redFive(Tile.Suit.Dot))
        val free = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val called = dot(5)
        val pon = meld(MeldType.Pon, called, listOf(called, dot(5), dot(5)))
        val player = FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(redFive, free), melds = listOf(pon)))
            .recordAction(GameAction.Pon(called.id, pon.tiles.drop(1).map { it.id }))

        assertEquals(setOf(free.id), discardable(player))
        assertEquals(setOf(free.id), threePlayerModule.discardableTileIds(table(player), player))
    }

    /** 不是剛吃碰時不限制。已立直時只能打出剛摸到的牌。 */
    @Test
    fun `other turns are not limited and riichi still allows only the drawn tile`() {
        val drawn = character(7)
        val plain = FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(character(1), character(2)), lastDrawn = drawn))
        val inRiichi = plain.copy(components = riichiDeclarationComponents(declarationTile = character(8)))

        assertNull(discardable(plain))
        assertEquals(setOf(drawn.id), discardable(inRiichi))
    }

    /** 吃或碰後只剩食替限制的牌可打時，不提供這次吃或碰。 */
    @Test
    fun `calls that leave only forbidden discards are not offered`() {
        val chiOnly = FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(character(4), character(5), character(3), character(6))))
        val chiWithFree = chiOnly.copy(hand = Hand(tiles = chiOnly.hand.tiles + FakeIdentifiedTileFactory.create(Tile.Honor.North)))
        val ponOnly = FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(dot(5), dot(5), dot(5))))

        assertTrue(actions(chiOnly, character(3), RelativeDirection.Left).none { it is GameAction.Chi })
        assertTrue(actions(chiWithFree, character(3), RelativeDirection.Left).any { it is GameAction.Chi })
        assertTrue(actions(ponOnly, dot(5), RelativeDirection.Across).none { it is GameAction.Pon })
    }

    /**
     * 剛吃完的玩家。
     *
     * @param called 吃進的牌。
     * @param with 用來吃的兩張手牌。
     * @param standing 吃完後的立牌。
     * @return 剛吃完、尚未捨牌的玩家。
     */
    private fun afterChi(called: IdentifiedTile, with: List<IdentifiedTile>, standing: List<IdentifiedTile>): MahjongPlayer {
        val chi = meld(MeldType.Chi, called, listOf(called) + with)
        return FakeMahjongPlayerFactory.create(hand = Hand(tiles = standing, melds = listOf(chi)))
            .recordAction(GameAction.Chi(called.id, with.map { it.id }))
    }

    /**
     * 建立一組鳴出的面子。
     *
     * @param type 面子種類。
     * @param called 鳴進的牌。
     * @param tiles 面子中所有的牌。
     * @return 面子。
     */
    private fun meld(type: MeldType, called: IdentifiedTile, tiles: List<IdentifiedTile>): Meld = Meld(
        type = type,
        tiles = tiles,
        sourceTile = called,
        sourceDirection = RelativeDirection.Left,
        publiclyRevealedTileIds = setOf(called.id),
        faceDownTileIds = emptySet(),
    )

    /**
     * 四人日本麻將規則下可以打出的牌。
     *
     * @param player 即將捨牌的玩家。
     * @return 可以打出的牌。不限制時為 null。
     */
    private fun discardable(player: MahjongPlayer) = module.discardableTileIds(table(player), player)

    /**
     * 回應別人捨牌時的合法動作。
     *
     * @param player 回應的玩家。
     * @param incoming 別人打出的牌。
     * @param source 打出者相對於玩家的方位。
     * @return 合法動作。
     */
    private fun actions(player: MahjongPlayer, incoming: IdentifiedTile, source: RelativeDirection): List<GameAction> = validator.getLegalActions(LegalActionTestContexts.discard(table(player), player, source, incoming))

    /**
     * 只有一位玩家、牌山還有牌的桌況。
     *
     * @param player 玩家。
     * @return 桌況。
     */
    private fun table(player: MahjongPlayer) = FakeTableStateFactory.create(
        players = listOf(player),
        tileWall = TileWall(List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }),
    )

    /**
     * 萬子。
     *
     * @param value 數字。
     * @return 牌張。
     */
    private fun character(value: Int): IdentifiedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, value))

    /**
     * 筒子。
     *
     * @param value 數字。
     * @return 牌張。
     */
    private fun dot(value: Int): IdentifiedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, value))
}
