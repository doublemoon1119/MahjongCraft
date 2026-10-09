package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證決策計時排程每個 tick 的接線：busy 與推進中略過、逾時去重、全域處理的順序與頻率。 */
class DecisionTickProcessorTest {
    /** 全域處理先暫停所有 busy 桌、再判定逾時，請求推進輪到的對局後最後同步倒數；請求不等待推進完成。 */
    @Test
    fun `global work wraps the advance requests in order`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (first, second) = fixture.games

        fixture.tick()

        assertEquals(
            listOf("reconcile $first", "reconcile $second", "timeouts", "request $first", "request $second", "synchronize"),
            fixture.events,
        )
    }

    /** 呈現播放中的對局這一輪不請求推進。 */
    @Test
    fun `busy games are not requested`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (busy, idle) = fixture.games
        fixture.busy += busy

        fixture.tick()

        assertEquals(listOf("request $idle"), fixture.requests())
    }

    /** 已在推進中的對局這一輪不再請求，留待下一次輪到它。 */
    @Test
    fun `games already advancing are not requested`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (advancing, idle) = fixture.games
        fixture.advancing += advancing

        fixture.tick()

        assertEquals(listOf("request $idle"), fixture.requests())
    }

    /** 逾時處理已請求推進的對局，同一輪不再請求。 */
    @Test
    fun `games requested by a timeout are not requested again`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (timedOut, other) = fixture.games
        fixture.timedOut += timedOut

        fixture.tick()

        assertEquals(listOf("request $other"), fixture.requests())
    }

    /** 一個週期內全域處理只做一次，每局只請求一次，請求分散在不同 tick。 */
    @Test
    fun `global work runs once per cycle while games are requested on their own ticks`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 20, games = games(2))

        val requestsPerTick = List(20) {
            fixture.events.clear()
            fixture.tick()
            fixture.requests().size to fixture.events.count { it == "synchronize" }
        }

        assertEquals(listOf(1, 1), requestsPerTick.map { it.first }.filter { it > 0 }, "Each game is requested once, on its own tick.")
        assertEquals(1, requestsPerTick.sumOf { it.second }, "Timers are synchronized once per cycle.")
    }

    /** 記錄每個呼叫的假資料。 */
    private class Fixture(cycleTicks: Int, val games: List<Uuid>) {
        val events = mutableListOf<String>()
        val busy = mutableSetOf<Uuid>()
        val advancing = mutableSetOf<Uuid>()
        val timedOut = mutableSetOf<Uuid>()

        val processor = DecisionTickProcessor(
            rotation = GameAdvanceRotation(cycleTicks),
            listGames = { games },
            reconcile = { gameId -> events += "reconcile $gameId" },
            settleTimeouts = {
                events += "timeouts"
                timedOut.toList()
            },
            isBusy = { gameId -> gameId in busy },
            isAdvancing = { gameId -> gameId in advancing },
            requestAdvance = { gameId -> events += "request $gameId" },
            synchronizeAll = { events += "synchronize" },
        )

        /** 前進一個 tick 並執行這個 tick 的處理。 */
        suspend fun tick() {
            processor.advanceTick()
            processor.process()
        }

        /** 這次記錄到的推進請求。 */
        fun requests(): List<String> = events.filter { it.startsWith("request") }

        companion object {
            /**
             * 建立假資料並先跑一個 tick 讓對局登記到輪轉中：對局在處理時才登記，登記前的位置已標記過，因此新對局在下一次
             * 輪到它的位置才請求推進（一個週期內）。登記那個 tick 的呼叫紀錄會清除。
             */
            suspend fun registered(cycleTicks: Int, games: List<Uuid>): Fixture = Fixture(cycleTicks, games).apply {
                tick()
                events.clear()
            }
        }
    }

    /** [count] 個新對局。 */
    private fun games(count: Int): List<Uuid> = List(count) { Uuid.random() }
}
