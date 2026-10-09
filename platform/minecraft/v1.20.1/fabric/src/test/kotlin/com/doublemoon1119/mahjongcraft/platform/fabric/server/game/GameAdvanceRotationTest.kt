package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證對局推進分散到各 tick 的位置分配、待推進標記與補做政策。 */
class GameAdvanceRotationTest {
    /** 對局平均分到週期的各個位置，每個 tick 推進的局數相同，每局每個週期推進一次。 */
    @Test
    fun `games spread evenly and advance once per cycle`() {
        val rotation = GameAdvanceRotation(CYCLE)
        val games = games(40)
        rotation.syncGames(games)

        val perTick = List(CYCLE) { rotation.tick() }

        assertTrue(perTick.all { it.size == 2 }, "Each tick should advance two of forty games, got ${perTick.map { it.size }}")
        assertEquals(games.toSet(), perTick.flatten().toSet())
        assertEquals(games.size, perTick.flatten().size)
    }

    /** 已結束的對局釋放位置、不再推進；新對局分到空出來的位置。 */
    @Test
    fun `ended games release their slot`() {
        val rotation = GameAdvanceRotation(CYCLE)
        val games = games(CYCLE)
        rotation.syncGames(games)
        val ended = games[5]
        val replacement = Uuid.random()

        rotation.syncGames(games - ended + replacement)
        val advanced = List(CYCLE) { rotation.tick() }

        assertFalse(advanced.flatten().contains(ended))
        assertEquals(listOf(replacement), advanced[5], "The new game takes the freed slot.")
    }

    /** 週期中途新增的對局在一個週期內推進一次，中途移除的對局不再推進。 */
    @Test
    fun `games added or removed mid cycle advance correctly`() {
        val rotation = GameAdvanceRotation(CYCLE)
        val games = games(40)
        rotation.syncGames(games)
        val firstHalf = List(CYCLE / 2) { rotation.tick() }.flatten()
        val added = Uuid.random()
        val removed = games.first { it !in firstHalf }

        rotation.syncGames(games - removed + added)
        val following = List(CYCLE) { rotation.tick() }.flatten()

        assertEquals(1, following.count { it == added })
        assertFalse(removed in following)
    }

    /** 處理工作等待超過一整個週期時每局只保留一次待推進；恢復後每 tick 推進的局數不超過上限，依序逐步消化。 */
    @Test
    fun `long waits keep one pending advance per game and catch up gradually`() {
        val rotation = GameAdvanceRotation(CYCLE)
        val games = games(40)
        rotation.syncGames(games)

        repeat(CYCLE + 5) { rotation.advanceTick() }
        assertEquals(games.size, rotation.dueCount, "A game waiting for more than a cycle is pending only once.")

        val budget = 40 / CYCLE + 1
        val batches = mutableListOf<List<Uuid>>()
        while (rotation.dueCount > 0) batches += rotation.tick()

        assertTrue(batches.all { it.size <= budget }, "Catch-up batches must stay within the budget, got ${batches.map { it.size }}")
        assertEquals(games.toSet(), batches.flatten().toSet())
    }

    /** 已由逾時處理推進的對局清除待推進標記，同一輪不重複推進。 */
    @Test
    fun `games advanced elsewhere are not advanced again`() {
        val rotation = GameAdvanceRotation(1)
        val games = games(3)
        rotation.syncGames(games)
        rotation.advanceTick()

        rotation.markAdvanced(listOf(games[1]))

        assertEquals(listOf(games[0], games[2]), rotation.takeDue())
    }

    /** 全域處理每個週期到期一次；延後完成時維持到期，完成後重新計算。 */
    @Test
    fun `global work is due once per cycle`() {
        val rotation = GameAdvanceRotation(CYCLE)

        repeat(CYCLE - 1) { rotation.advanceTick() }
        assertFalse(rotation.globalDue)
        rotation.advanceTick()
        assertTrue(rotation.globalDue)
        rotation.advanceTick()
        assertTrue(rotation.globalDue, "Global work stays due until it completes.")

        rotation.completeGlobal()
        assertFalse(rotation.globalDue)
    }

    /** 前進一個 tick 並取出這個 tick 要推進的對局。 */
    private fun GameAdvanceRotation.tick(): List<Uuid> {
        advanceTick()
        return takeDue()
    }

    /** [count] 個新對局。 */
    private fun games(count: Int): List<Uuid> = List(count) { Uuid.random() }

    private companion object {
        /** 測試使用的週期 tick 數，與正式排程相同。 */
        const val CYCLE = 20
    }
}
