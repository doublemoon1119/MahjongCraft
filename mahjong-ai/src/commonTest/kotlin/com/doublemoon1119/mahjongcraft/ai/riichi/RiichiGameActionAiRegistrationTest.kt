package com.doublemoon1119.mahjongcraft.ai.riichi

import com.doublemoon1119.mahjongcraft.ai.ExtensionCommandCandidate
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.context
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.extensionRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.hand
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.m
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.p
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.player
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.s
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.table
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證日麻立直 handler 產生的候選。 */
class RiichiGameActionAiRegistrationTest {
    /** 二三四萬、四五六筒、七八九筒、二三四條、五條，摸到北：打北、打二條或打五條都聽牌。 */
    @Test
    fun `every discard that keeps tenpai becomes a riichi candidate including the drawn tile`() {
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(m(2), m(3), m(4), p(4), p(5), p(6), p(7), p(8), p(9), s(2), s(3), s(4), s(5)),
                drawn = Tile.Honor.North,
            ),
        )
        val table = table(listOf(player(Wind.EAST), self))
        val drawn = checkNotNull(self.hand.lastDrawn)
        val two = self.hand.tiles.first { it.tile == s(2) }
        val five = self.hand.tiles.first { it.tile == s(5) }

        val candidates = extensionRegistry.createCandidates(RiichiGameAction.Riichi, context(table, self, legalActions = listOf(RIICHI_GAME_ACTION)))

        assertEquals(
            listOf(two, five, drawn).map { tile ->
                ExtensionCommandCandidate(GameCommand.Extension(RiichiGameCommand(tile.id)), tile.id, RIICHI_GAME_ACTION)
            }.toSet(),
            candidates.toSet(),
        )
    }

    /** 有暗槓的手牌仍以暗槓作為一組面子判斷聽牌。 */
    @Test
    fun `a closed kan counts as a set when checking tenpai`() {
        val kan = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.White) },
            sourceDirection = RelativeDirection.Self,
        )
        val self = player(
            Wind.SOUTH,
            hand = hand(
                listOf(p(4), p(5), p(6), p(7), p(8), p(9), s(2), s(3), s(4), s(5)),
                drawn = Tile.Honor.North,
                melds = listOf(kan),
            ),
        )
        val table = table(listOf(player(Wind.EAST), self))

        val candidates = extensionRegistry.createCandidates(RiichiGameAction.Riichi, context(table, self, legalActions = listOf(RIICHI_GAME_ACTION)))

        val expected = setOf(checkNotNull(self.hand.lastDrawn).id) + self.hand.tiles.filter { it.tile == s(2) || it.tile == s(5) }.map { it.id }
        assertEquals(expected, candidates.mapNotNull { it.discardTileId }.toSet())
    }
}
