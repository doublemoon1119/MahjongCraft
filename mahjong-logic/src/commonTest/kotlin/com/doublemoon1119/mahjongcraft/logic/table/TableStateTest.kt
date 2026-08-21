package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 [TableState.localRoundNumber] 換算邏輯。 */
class TableStateTest {
    private val fourPlayers = List(4) { FakeMahjongPlayerFactory.create() }

    /** 場風內第一局（跨場風累計局數等於玩家人數的整數倍加一）應換算回 `1`。 */
    @Test
    fun `test localRoundNumber resets to 1 at the start of a wind`() {
        val state = FakeTableStateFactory.create(players = fourPlayers, roundNumber = 1)
        assertEquals(1, state.localRoundNumber)

        val nextWindFirstRound = FakeTableStateFactory.create(players = fourPlayers, roundNumber = 5)
        assertEquals(1, nextWindFirstRound.localRoundNumber)
    }

    /** 場風內非第一局應正確換算回場風內的局數，不受跨場風累計局數影響。 */
    @Test
    fun `test localRoundNumber wraps within a wind`() {
        val state = FakeTableStateFactory.create(players = fourPlayers, roundNumber = 7)
        assertEquals(3, state.localRoundNumber)
    }
}
