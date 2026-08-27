package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [TableState.localRoundNumber] 換算邏輯，以及 [TableState] 的 active/finished 玩家概念。 */
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

    /** 沒有任何 finished 玩家時，所有玩家都應視為 active。 */
    @Test
    fun `test activePlayers and isPlayerActive when no one is finished`() {
        val state = FakeTableStateFactory.create(players = fourPlayers)
        assertEquals(fourPlayers, state.activePlayers)
        fourPlayers.forEach { assertTrue(state.isPlayerActive(it.id)) }
    }

    /** finished 玩家不應出現在 [TableState.activePlayers]，且 [TableState.isPlayerActive] 應回報 false。 */
    @Test
    fun `test activePlayers and isPlayerActive exclude finished players`() {
        val finished = setOf(fourPlayers[1].id, fourPlayers[3].id)
        val state = FakeTableStateFactory.create(players = fourPlayers, finishedPlayerIds = finished)

        assertEquals(listOf(fourPlayers[0], fourPlayers[2]), state.activePlayers)
        assertTrue(state.isPlayerActive(fourPlayers[0].id))
        assertTrue(!state.isPlayerActive(fourPlayers[1].id))
        assertTrue(state.isPlayerActive(fourPlayers[2].id))
        assertTrue(!state.isPlayerActive(fourPlayers[3].id))
    }

    /** 沒有 finished 玩家時，[TableState.nextActivePlayerAfter] 的行為應等同座位表上物理相鄰的下一位。 */
    @Test
    fun `test nextActivePlayerAfter behaves like getNextPlayer when no one is finished`() {
        val state = FakeTableStateFactory.create(players = fourPlayers, currentPlayerIndex = 0)
        assertEquals(fourPlayers[1], state.nextActivePlayerAfter(fourPlayers[0].id))
        assertEquals(fourPlayers[2], state.nextActivePlayerAfter(fourPlayers[1].id))
        assertEquals(fourPlayers[0], state.nextActivePlayerAfter(fourPlayers[3].id))
    }

    /** 起算玩家的下一位剛好 finished 時，應跳過他找到下一位 active 玩家。 */
    @Test
    fun `test nextActivePlayerAfter skips a single finished player`() {
        val state = FakeTableStateFactory.create(
            players = fourPlayers,
            currentPlayerIndex = 0,
            finishedPlayerIds = setOf(fourPlayers[1].id),
        )
        assertEquals(fourPlayers[2], state.nextActivePlayerAfter(fourPlayers[0].id))
    }

    /** 連續多位玩家 finished 時，應一路跳過直到找到下一位 active 玩家，必要時繞回座位表開頭。 */
    @Test
    fun `test nextActivePlayerAfter skips multiple consecutive finished players and wraps around`() {
        val state = FakeTableStateFactory.create(
            players = fourPlayers,
            currentPlayerIndex = 0,
            finishedPlayerIds = setOf(fourPlayers[1].id, fourPlayers[2].id, fourPlayers[3].id),
        )
        // 座位表上除了起算玩家自己以外只剩他自己是 active，繞一圈後應回到自己。
        assertEquals(fourPlayers[0], state.nextActivePlayerAfter(fourPlayers[0].id))
    }

    /** 桌上只剩一位 active 玩家時，仍能正確繞一圈回到唯一的 active 玩家自己。 */
    @Test
    fun `test nextActivePlayerAfter wraps back to the sole remaining active player`() {
        val state = FakeTableStateFactory.create(
            players = fourPlayers,
            currentPlayerIndex = 3,
            finishedPlayerIds = setOf(fourPlayers[0].id, fourPlayers[1].id, fourPlayers[2].id),
        )
        assertEquals(fourPlayers[3], state.nextActivePlayerAfter(fourPlayers[3].id))
    }

    /**
     * 不在本桌的 Uuid 一律不是 active——不能因為「不在 finished 集合裡」就當成可以行動，那會讓打錯的
     * 識別碼安靜地通過檢查。
     */
    @Test
    fun `test isPlayerActive is false for a player who is not seated at this table`() {
        val seated = FakeTableStateFactory.create(players = fourPlayers)
        assertTrue(!seated.isPlayerActive(Uuid.random()))

        val withFinished = FakeTableStateFactory.create(
            players = fourPlayers,
            currentPlayerIndex = 0,
            finishedPlayerIds = setOf(fourPlayers[1].id),
        )
        assertTrue(!withFinished.isPlayerActive(Uuid.random()))
    }

    /** `finishedPlayerIds` 內含不屬於本桌的玩家時，建構當下就該拋出例外。 */
    @Test
    fun `test constructing TableState throws when finishedPlayerIds contains an outsider`() {
        assertFailsWith<IllegalArgumentException> {
            FakeTableStateFactory.create(
                players = fourPlayers,
                currentPlayerIndex = 0,
                finishedPlayerIds = setOf(fourPlayers[1].id, Uuid.random()),
            )
        }
    }

    /** 起算玩家不在桌上時應拋出例外。 */
    @Test
    fun `test nextActivePlayerAfter throws when starting player is not seated`() {
        val state = FakeTableStateFactory.create(players = fourPlayers)
        assertFailsWith<IllegalArgumentException> {
            state.nextActivePlayerAfter(Uuid.random())
        }
    }

    /**
     * 建構 [TableState] 時，若 `currentPlayerIndex` 指向一位已經 finished 的玩家，應立即拋出例外。
     *
     * 這是所有回合推進呼叫點都必須改用 [nextActivePlayerAfter] 而非直接 modulo 運算的防呆——
     * 一旦有呼叫點遺漏，這裡會在建構當下就炸出來，而不是等到 in-game 出現「finished 玩家收到回合」
     * 這種難以定位的 bug。全員 finished（因此不存在任何 active 玩家）必然也會撞到這個檢查，因為
     * `currentPlayer` 此時必定屬於 [finishedPlayerIds]。
     */
    @Test
    fun `test constructing TableState throws when currentPlayerIndex points at a finished player`() {
        assertFailsWith<IllegalArgumentException> {
            FakeTableStateFactory.create(
                players = fourPlayers,
                currentPlayerIndex = 1,
                finishedPlayerIds = setOf(fourPlayers[1].id),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            FakeTableStateFactory.create(
                players = fourPlayers,
                currentPlayerIndex = 0,
                finishedPlayerIds = fourPlayers.map { it.id }.toSet(),
            )
        }
    }
}
