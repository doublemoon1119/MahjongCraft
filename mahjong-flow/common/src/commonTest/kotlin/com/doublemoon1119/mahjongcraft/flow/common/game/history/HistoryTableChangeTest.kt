package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證桌況差異能記錄並重建本局已公開的手牌。 */
class HistoryTableChangeTest {
    private val before = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

    /** 已公開手牌改變時，差異記下新集合，套用後還原成同一份桌況。 */
    @Test
    fun `change records and restores revealed hand tiles`() {
        val after = before.copy(revealedHandTileIds = setOf(Uuid.random()))

        val change = assertNotNull(HistoryTableChange.between(before, after))

        assertEquals(after.revealedHandTileIds, change.revealedHandTileIds)
        assertEquals(after, change.applyTo(before))
    }

    /** 已公開手牌沒有改變時不記錄。 */
    @Test
    fun `unchanged revealed hand tiles are not recorded`() {
        val after = before.copy(currentPlayerIndex = before.currentPlayerIndex)

        assertNull(assertNotNull(HistoryTableChange.between(before, after)).revealedHandTileIds)
    }

    /** 流局公開手牌與等待牌改變時，差異能記下並還原完整資料。 */
    @Test
    fun `change records and restores revealed hand settlements`() {
        val settlement = RevealedHandSettlement(
            playerId = before.players.first().id,
            waitingTiles = setOf(Tile.Honor.North),
        )
        val revealedTileId = Uuid.random()
        val after = before.copy(
            revealedHandTileIds = setOf(revealedTileId),
            revealedHandSettlements = listOf(settlement),
        )

        val change = assertNotNull(HistoryTableChange.between(before, after))

        assertEquals(setOf(revealedTileId), change.revealedHandTileIds)
        assertEquals(listOf(settlement), change.revealedHandSettlements)
        assertEquals(after, change.applyTo(before))
    }

    /** 流局公開資料清空時，空集合不能被誤認為未改變。 */
    @Test
    fun `change records clearing revealed hand settlements`() {
        val settlement = RevealedHandSettlement(
            playerId = before.players.first().id,
            waitingTiles = setOf(Tile.Honor.North),
        )
        val source = before.copy(revealedHandSettlements = listOf(settlement))
        val after = source.copy(revealedHandSettlements = emptyList())

        val change = assertNotNull(HistoryTableChange.between(source, after))

        assertEquals(emptyList(), change.revealedHandSettlements)
        assertEquals(after, change.applyTo(source))
    }

    /** 逐輪擲骰結果改變時，差異能保留非空的前後序列。 */
    @Test
    fun `change records and restores opening dice rolls`() {
        val playerId = before.players.first().id
        val first = OpeningDiceRoll(0, playerId, DiceRollResult.of(listOf(1, 6)))
        val second = OpeningDiceRoll(1, playerId, DiceRollResult.of(listOf(3, 4)))
        val source = before.copy(openingDiceRolls = listOf(first))
        val after = source.copy(openingDiceRolls = listOf(first, second))

        val change = assertNotNull(HistoryTableChange.between(source, after))

        assertEquals(listOf(first, second), change.openingDiceRolls)
        assertEquals(after, change.applyTo(source))
    }
}
