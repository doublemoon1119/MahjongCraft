package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證流局時公開手牌的共用記錄：公開的範圍、本局已完成的玩家，以及由規則決定的公開對象。 */
class DrawRevealedHandsTest {
    /** 剛摸到北的玩家：兩張立牌加上一張摸牌。 */
    private val drawingPlayer = FakeMahjongPlayerFactory.create(
        initialSeat = Wind.EAST,
        hand = FakeHandFactory.create(listOf(Tile.Honor.East, Tile.Honor.South))
            .copy(lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.North)),
    )

    /** 另一位持有一張西的玩家。 */
    private val otherPlayer = FakeMahjongPlayerFactory.create(
        initialSeat = Wind.SOUTH,
        hand = FakeHandFactory.create(listOf(Tile.Honor.West)),
    )

    /** 兩位玩家的日麻桌況。 */
    private val table = FakeTableStateFactory.create(players = listOf(drawingPlayer, otherPlayer), config = RiichiRuleConfig())

    /** 公開的是玩家全部立牌，包含剛摸到的牌；本局已完成的玩家不公開。 */
    @Test
    fun `revealing a hand covers every standing tile except for finished players`() {
        val result = table.copy(finishedPlayerIds = setOf(otherPlayer.id))
            .revealHandsOf(listOf(drawingPlayer.id, otherPlayer.id))

        assertEquals(drawingPlayer.hand.standingTiles.map { it.id }.toSet(), result.revealedHandTileIds)
        assertEquals(3, result.revealedHandTileIds.size)
    }

    /** 途中流局記錄給所有玩家；九種九牌只公開宣告者的手牌，系統判定的流局不公開任何手牌。 */
    @Test
    fun `abortive draws reveal only the hands the rule asks for`() {
        val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
        val reason = RiichiExhaustiveDrawReason.KyuushuKyuuhai

        val declared = table.recordAbortiveDraw(reason, declarerId = drawingPlayer.id, module = module)
        val systemJudged = table.recordAbortiveDraw(reason, declarerId = null, module = module)

        declared.players.forEach { assertEquals(GameAction.ExhaustiveDraw(reason), it.actionHistory.last()) }
        assertEquals(drawingPlayer.hand.standingTiles.map { it.id }.toSet(), declared.revealedHandTileIds)
        assertEquals(emptySet(), systemJudged.revealedHandTileIds)
    }
}
