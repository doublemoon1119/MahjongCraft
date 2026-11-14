package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證逐輪移出並補牌的順序、暫停與無牌可補。 */
class TileReplacementRoundsTest {
    /** 每位玩家一次移出全部指定牌；補到的指定牌等這一輪所有玩家處理完，下一輪再處理。 */
    @Test
    fun `each round sets aside all marked tiles and defers replacements that are marked again`() {
        val first = marked()
        val second = marked()
        val third = marked()
        val chained = marked()
        val (plainA, plainB, plainC) = List(3) { plain() }
        val table = table(hands = listOf(listOf(first, second), emptyList(), listOf(third)), wall = listOf(chained, plainA, plainB, plainC))
        val (p0, _, p2) = table.players.map { it.id }

        val replacementsRemaining = mutableListOf<Int>()
        val outcome = assertIs<TileReplacementRounds.Outcome.Completed>(
            value = TileReplacementRounds.process(
                tableState = table,
                playerOrderIds = table.players.map { it.id },
                rules = Rules(observedReplacementsRemaining = replacementsRemaining),
            ),
        )

        assertEquals(
            expected = listOf(
                setAside(p0, listOf(first, second)),
                replaced(p0, listOf(chained, plainA)),
                setAside(p2, listOf(third)),
                replaced(p2, listOf(plainB)),
                setAside(p0, listOf(chained)),
                replaced(p0, listOf(plainC)),
            ),
            actual = outcome.steps.map { it.summary() },
        )
        assertEquals(listOf(first, second, chained), outcome.tableState.setAsideOf(p0))
        // 每一步帶著當下的桌況：第一輪移出後只有前兩張在移出區，第二輪才移出補到的那張。
        assertEquals(listOf(first, second), outcome.steps[0].tableState.setAsideOf(p0))
        assertEquals(listOf(first, second), outcome.steps[3].tableState.setAsideOf(p0))
        assertEquals(listOf(first, second, chained), outcome.steps[4].tableState.setAsideOf(p0))
        assertEquals(listOf(2, 1, 1, 1), replacementsRemaining)
        // 補到又要移出的牌留到下一輪：前四步在第 1 輪，最後兩步在第 2 輪。
        assertEquals(listOf(1, 1, 1, 1, 2, 2), outcome.steps.map { it.round })
    }

    /** 移出後暫停的位置帶著尚未補的張數，從那裡繼續時先補牌，再接著處理下一位玩家。 */
    @Test
    fun `a pause after setting aside resumes with the replacements still due`() {
        val (first, second, third) = List(3) { marked() }
        val (plainA, plainB, plainC) = List(3) { plain() }
        val table = table(hands = listOf(listOf(first, second), listOf(third)), wall = listOf(plainA, plainB, plainC))
        val (p0, p1) = table.players.map { it.id }
        val order = table.players.map { it.id }

        val paused = assertIs<TileReplacementRounds.Outcome.Paused<String>>(
            value = TileReplacementRounds.process(table, order, Rules(pauseAfterSetAsideBy = p0)),
        )
        val resumed = assertIs<TileReplacementRounds.Outcome.Completed>(TileReplacementRounds.process(paused.tableState, order, Rules(), paused.position))

        assertEquals(TileReplacementRounds.Position(playerOrderIndex = 0, replacementsDue = 2, round = 1), paused.position)
        assertEquals(
            expected = listOf(
                replaced(p0, listOf(plainA, plainB)),
                setAside(p1, listOf(third)),
                replaced(p1, listOf(plainC)),
            ),
            actual = resumed.steps.map { it.summary() },
        )
    }

    /** 補完牌後暫停的位置指向下一位玩家。 */
    @Test
    fun `a pause after replacement resumes with the next player`() {
        val (first, second) = List(2) { marked() }
        val table = table(hands = listOf(listOf(first), listOf(second)), wall = List(2) { plain() })
        val order = table.players.map { it.id }

        val paused = assertIs<TileReplacementRounds.Outcome.Paused<String>>(
            value = TileReplacementRounds.process(table, order, Rules(pauseAfterReplacementBy = order[0])),
        )

        assertEquals(TileReplacementRounds.Position(playerOrderIndex = 1, replacementsDue = 0, round = 1), paused.position)
    }

    /** 最後一位玩家補完牌後暫停時，繼續的位置是下一輪的第一位玩家。 */
    @Test
    fun `a pause after the last replacement of a round resumes in the next round`() {
        val (first, second) = List(2) { marked() }
        val table = table(hands = listOf(listOf(first), listOf(second)), wall = List(2) { plain() })
        // 只讓前兩位玩家參與，第二位就是這一輪的最後一位。
        val order = table.players.map { it.id }.take(2)

        val paused = assertIs<TileReplacementRounds.Outcome.Paused<String>>(
            value = TileReplacementRounds.process(table, order, Rules(pauseAfterReplacementBy = order[1])),
        )

        assertEquals(TileReplacementRounds.Position(playerOrderIndex = 0, replacementsDue = 0, round = 2), paused.position)
    }

    /** 補到一半沒有牌可以補時停下，已補到的牌仍回報。 */
    @Test
    fun `running out of replacements stops with the tiles already drawn`() {
        val (first, second) = List(2) { marked() }
        val only = plain()
        val table = table(hands = listOf(listOf(first, second)), wall = listOf(only))
        val p0 = table.players.first().id

        val exhausted = assertIs<TileReplacementRounds.Outcome.Exhausted>(TileReplacementRounds.process(table, listOf(p0), Rules()))

        assertEquals(p0, exhausted.playerId)
        assertEquals(
            expected = listOf(setAside(p0, listOf(first, second)), replaced(p0, listOf(only))),
            actual = exhausted.steps.map { it.summary() },
        )
    }

    /**
     * 以紅中為指定牌、從活牌前端補牌到立牌的測試規則。
     *
     * @property pauseAfterSetAsideBy 這位玩家移出後暫停；不暫停時為 null。
     * @property pauseAfterReplacementBy 這位玩家補完牌後暫停；不暫停時為 null。
     * @property observedReplacementsRemaining 記錄每次補牌前仍待補入的張數。
     */
    private class Rules(
        private val pauseAfterSetAsideBy: Uuid? = null,
        private val pauseAfterReplacementBy: Uuid? = null,
        private val observedReplacementsRemaining: MutableList<Int>? = null,
    ) : TileReplacementRounds.Rules<String> {
        override fun isSetAside(tile: IdentifiedTile): Boolean = tile.tile == MARKED

        override fun setAside(tableState: TableState, playerId: Uuid, tile: IdentifiedTile): TableState = tableState.copy(
            players = tableState.players.map { player ->
                if (player.id != playerId) {
                    player
                } else {
                    player.copy(
                        hand = player.hand.copy(tiles = player.hand.tiles.filterNot { it.id == tile.id }),
                        tileAreas = player.tileAreas.withArea(AREA, player.tileAreas[AREA]?.tiles.orEmpty() + tile),
                    )
                }
            },
        )

        override fun replace(
            tableState: TableState,
            playerId: Uuid,
            replacementsRemaining: Int,
        ): PlayerDraw? {
            observedReplacementsRemaining?.add(replacementsRemaining)
            val (tile, wall) = tableState.tileWall.draw()
            if (tile == null) return null
            return PlayerDraw(
                tableState = tableState.copy(
                    players = tableState.players.map { if (it.id == playerId) it.copy(hand = it.hand.copy(tiles = it.hand.tiles + tile)) else it },
                    tileWall = wall,
                ),
                tile = tile,
            )
        }

        override fun pauseAfterSetAside(tableState: TableState, playerId: Uuid, tiles: List<IdentifiedTile>): String? = "set aside".takeIf { playerId == pauseAfterSetAsideBy }

        override fun pauseAfterReplacement(tableState: TableState, playerId: Uuid): String? = "replaced".takeIf { playerId == pauseAfterReplacementBy }
    }

    /**
     * 建立指定手牌與活牌的桌況。
     *
     * @param hands 依座位排列的手牌；沒有列出的座位沒有手牌。
     * @param wall 活牌，依摸牌順序排列。
     * @return 桌況。
     */
    private fun table(hands: List<List<IdentifiedTile>>, wall: List<IdentifiedTile>): TableState = FakeTableStateFactory.create(
        players = Wind.entries.mapIndexed { index, wind -> FakeMahjongPlayerFactory.create(wind, hand = Hand(tiles = hands.getOrElse(index) { emptyList() })) },
        tileWall = TileWall(wall),
    )

    /**
     * 取得玩家移出的牌。
     *
     * @param playerId 玩家識別碼。
     * @return 移出的牌。
     */
    private fun TableState.setAsideOf(playerId: Uuid): List<IdentifiedTile> = players.first { it.id == playerId }.tileAreas[AREA]?.tiles.orEmpty()

    /**
     * 建立一張指定牌。
     *
     * @return 紅中。
     */
    private fun marked(): IdentifiedTile = FakeIdentifiedTileFactory.create(MARKED)

    /**
     * 建立一張一般牌。
     *
     * @return 一筒。
     */
    private fun plain(): IdentifiedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))

    /** 測試常數。 */
    private companion object {
        /** 指定要移出的牌。 */
        val MARKED: Tile = Tile.Honor.Red

        /** 移出區識別碼。 */
        const val AREA = "test:set_aside"
    }

    /** 只比較步驟的種類、玩家與牌，不比較每一步的桌況。 */
    private fun TileReplacementRounds.Step.summary(): Triple<String, Uuid, List<IdentifiedTile>> = Triple(if (this is TileReplacementRounds.Step.SetAside) "set-aside" else "replaced", playerId, tiles)

    /** 移出步驟的摘要。 */
    private fun setAside(playerId: Uuid, tiles: List<IdentifiedTile>) = Triple("set-aside", playerId, tiles)

    /** 補牌步驟的摘要。 */
    private fun replaced(playerId: Uuid, tiles: List<IdentifiedTile>) = Triple("replaced", playerId, tiles)
}
