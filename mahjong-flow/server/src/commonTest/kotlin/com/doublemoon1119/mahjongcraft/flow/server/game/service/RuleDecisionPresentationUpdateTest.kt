package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 規則選擇結果的呈現：依序送出中間步驟，最後是提交的桌況。 */
class RuleDecisionPresentationUpdateTest {
    /** 桌況完全相同、也沒有中間步驟時，不送出任何步驟。 */
    @Test
    fun `unchanged table publishes no steps`() {
        val table = FakeTableStateFactory.create(config = RiichiRuleConfig())
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(table, table).publish(publisher)

        assertTrue(publisher.getPublishedTableStepBatches(table.id).isEmpty())
    }

    /** 沒有中間步驟時，只送出提交的桌況這一步。 */
    @Test
    fun `changed table without intermediate steps publishes the committed state`() {
        val (previous, current) = exchange()
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(previous, current).publish(publisher)

        assertEquals(listOf(listOf(TableStep(current, emptyList()))), publisher.getPublishedTableStepBatches(current.id))
    }

    /** 中間步驟依序送出，最後接上提交的桌況。 */
    @Test
    fun `intermediate steps precede the committed state`() {
        val (previous, current) = exchange()
        val intermediate = TableStep(previous.copy(tileWall = TileWall(emptyList())), emptyList())
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(previous, current, listOf(intermediate)).publish(publisher)

        assertEquals(listOf(listOf(intermediate, TableStep(current, emptyList()))), publisher.getPublishedTableStepBatches(current.id))
    }

    /** 最後一個中間步驟已經是提交的桌況時，不重複送出同一份桌況。 */
    @Test
    fun `committed state is not repeated after an identical last step`() {
        val (previous, current) = exchange()
        val last = TableStep(current, emptyList())
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(previous, current, listOf(last)).publish(publisher)

        assertEquals(listOf(listOf(last)), publisher.getPublishedTableStepBatches(current.id))
    }

    /**
     * 建立一次把手牌與牌牆的牌互換的前後桌況。
     *
     * @return 互換前與互換後的桌況。
     */
    private fun exchange() = run {
        val handTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val wallTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val previous = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            players = listOf(FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(handTile)))),
            tileWall = TileWall(listOf(wallTile)),
        )
        val current = previous.copy(
            players = listOf(previous.players.single().copy(hand = Hand(tiles = listOf(wallTile)))),
            tileWall = TileWall(listOf(handTile)),
        )
        previous to current
    }
}
