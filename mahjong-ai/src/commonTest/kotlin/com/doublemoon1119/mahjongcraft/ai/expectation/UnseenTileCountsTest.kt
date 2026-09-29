package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.hand
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.m
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.module
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.p
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.player
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.table
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
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
        val counts = UnseenTileCounts.from(table.toSnapshot(setOf(self.id)), self.id, module, countsVisibleTiles = false)

        assertEquals(1, counts[m(5)])
        assertEquals(4, counts[Tile.Honor.North])
        assertEquals(136 - 3, counts.total)
    }

    /** 扣除所有可見牌時，對手牌河中的牌也一併扣除。 */
    @Test
    fun `counting visible tiles also deducts discards`() {
        val counts = UnseenTileCounts.from(table.toSnapshot(setOf(self.id)), self.id, module, countsVisibleTiles = true)

        assertEquals(0, counts[m(5)])
        assertEquals(3, counts[Tile.Honor.North])
    }

    /** 赤五與普通五算同一種牌。 */
    @Test
    fun `a red five is counted as a five`() {
        val withRed = player(Wind.SOUTH, hand = hand(listOf(RiichiTileTypes.redFive(Tile.Suit.Dot), p(5))))
        val counts = UnseenTileCounts.from(table(listOf(opponent, withRed)).toSnapshot(setOf(withRed.id)), withRed.id, module, countsVisibleTiles = false)

        assertEquals(2, counts[p(5)])
        assertEquals(34, counts.kinds.size)
    }

    /** 假設摸進一張後，該種牌的未見張數減一。 */
    @Test
    fun `drawing a kind removes one unseen copy`() {
        val counts = UnseenTileCounts.from(table.toSnapshot(setOf(self.id)), self.id, module, countsVisibleTiles = false)

        assertEquals(3, counts.without(Tile.Honor.North)[Tile.Honor.North])
        assertEquals(counts.total - 1, counts.without(Tile.Honor.North).total)
    }
}
