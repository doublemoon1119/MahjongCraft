package com.doublemoon1119.mahjongcraft.flow.common.game.history

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
}
