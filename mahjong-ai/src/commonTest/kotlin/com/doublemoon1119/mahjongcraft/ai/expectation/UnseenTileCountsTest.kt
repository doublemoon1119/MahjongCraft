package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.hand
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.m
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.module
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.p
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.player
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.table
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證未見牌計數依資訊範圍扣除可見牌。 */
class UnseenTileCountsTest {
    /** 自己手上兩張、摸到一張、對手牌河一張五萬的桌況。 */
    private val self = player(Wind.SOUTH, hand = hand(listOf(m(5), m(5)), drawn = m(5)))
    private val opponent = player(Wind.EAST, discards = listOf(m(5), Tile.Honor.North))
    private val table = table(listOf(opponent, self))

    /** 只扣除自己的手牌時，對手牌河中的牌仍算未見；剛摸到的牌只算一次。 */
    @Test
    fun `without counting visible tiles only the own hand is deducted`() {
        val counts = UnseenTileCounts.from(table.toSnapshot(setOf(self.id), setAsideTiles = { emptyList() }), self.id, module, countsVisibleTiles = false)

        assertEquals(1, counts[m(5)])
        assertEquals(4, counts[Tile.Honor.North])
        assertEquals(136 - 3, counts.total)
    }

    /** 扣除所有可見牌時，對手牌河中的牌也一併扣除；看到的普通五萬多於牌山中的張數時，改從赤五萬扣除。 */
    @Test
    fun `counting visible tiles also deducts discards`() {
        val counts = UnseenTileCounts.from(table.toSnapshot(setOf(self.id), setAsideTiles = { emptyList() }), self.id, module, countsVisibleTiles = true)

        assertEquals(0, counts[m(5)])
        assertEquals(3, counts[Tile.Honor.North])
    }

    /** 赤五與普通五算同一種牌。 */
    @Test
    fun `a red five is counted as a five`() {
        val withRed = player(Wind.SOUTH, hand = hand(listOf(RiichiTileTypes.redFive(Tile.Suit.Dot), p(5))))
        val counts = UnseenTileCounts.from(table(listOf(opponent, withRed)).toSnapshot(setOf(withRed.id), setAsideTiles = { emptyList() }), withRed.id, module, countsVisibleTiles = false)

        assertEquals(2, counts[p(5)])
        assertEquals(34, counts.kinds.size)
    }

    /** 赤五與普通五另外記錄各自的未見張數：手上一張普通五筒時，剩下普通五筒 2 張、赤五筒 1 張。 */
    @Test
    fun `red and plain fives are tracked separately`() {
        val withPlain = player(Wind.SOUTH, hand = hand(listOf(p(5))))
        val counts = UnseenTileCounts.from(table(listOf(opponent, withPlain)).toSnapshot(setOf(withPlain.id), setAsideTiles = { emptyList() }), withPlain.id, module, countsVisibleTiles = false)

        assertEquals(setOf(p(5) to 2, RED_FIVE_DOT to 1), counts.faces(p(5)).toSet())
        assertEquals(listOf(Tile.Honor.North to 4), counts.faces(Tile.Honor.North))
    }

    /** 假設摸進赤五筒後，只少掉赤五筒；張數為 0 的牌面不列出。 */
    @Test
    fun `drawing a red five removes only the red copy`() {
        val withPlain = player(Wind.SOUTH, hand = hand(listOf(p(5))))
        val counts = UnseenTileCounts.from(table(listOf(opponent, withPlain)).toSnapshot(setOf(withPlain.id), setAsideTiles = { emptyList() }), withPlain.id, module, countsVisibleTiles = false)
            .without(RED_FIVE_DOT)

        assertEquals(listOf(p(5) to 2), counts.faces(p(5)))
        assertEquals(2, counts[p(5)])
    }

    /** 假設摸進一張後，該種牌的未見張數減一。 */
    @Test
    fun `drawing a kind removes one unseen copy`() {
        val counts = UnseenTileCounts.from(table.toSnapshot(setOf(self.id), setAsideTiles = { emptyList() }), self.id, module, countsVisibleTiles = false)

        assertEquals(3, counts.without(Tile.Honor.North)[Tile.Honor.North])
        assertEquals(counts.total - 1, counts.without(Tile.Honor.North).total)
    }

    /** 自己拔出的北一律扣除；扣除所有可見牌時，他家拔出的北也一併扣除。 */
    @Test
    fun `pulled north tiles are deducted`() {
        val threePlayerModule = ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, ThreePlayerRiichiRuleConfig())
        val puller = player(Wind.EAST, hand = hand(listOf(p(1))))
            .copy(playerRuleState = RiichiPlayerState(nukiDoraTiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.North))))
        val otherPuller = player(Wind.SOUTH)
            .copy(playerRuleState = RiichiPlayerState(nukiDoraTiles = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.North) }))
        val threePlayerTable = FakeTableStateFactory.create(
            players = listOf(puller, otherPuller, player(Wind.WEST)),
            config = ThreePlayerRiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        val snapshot = threePlayerTable.toSnapshot(setOf(puller.id), setAsideTiles = threePlayerModule::setAsideTiles)

        val ownOnly = UnseenTileCounts.from(snapshot, puller.id, threePlayerModule, countsVisibleTiles = false)
        val allVisible = UnseenTileCounts.from(snapshot, puller.id, threePlayerModule, countsVisibleTiles = true)

        assertEquals(3, ownOnly[Tile.Honor.North])
        assertEquals(1, allVisible[Tile.Honor.North])
    }

    private companion object {
        val RED_FIVE_DOT: Tile = RiichiTileTypes.redFive(Tile.Suit.Dot)
    }
}
