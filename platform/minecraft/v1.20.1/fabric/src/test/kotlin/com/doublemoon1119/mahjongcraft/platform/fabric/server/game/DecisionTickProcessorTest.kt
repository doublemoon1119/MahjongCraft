package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證決策計時排程每個 tick 的接線：busy 略過、逾時去重、全域處理的順序與頻率。 */
class DecisionTickProcessorTest {
    /** 全域處理先暫停所有 busy 桌、再判定逾時，推進輪到的對局後最後同步倒數。 */
    @Test
    fun `global work wraps the advances in order`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (first, second) = fixture.games

        fixture.tick()

        assertEquals(
            listOf("reconcile $first", "reconcile $second", "timeouts", "drive $first", "autoDraw $first", "drive $second", "autoDraw $second", "synchronize"),
            fixture.events,
        )
    }

    /** 呈現播放中的對局這一輪不推進。 */
    @Test
    fun `busy games are not advanced`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (busy, idle) = fixture.games
        fixture.busy += busy

        fixture.tick()

        assertEquals(listOf("drive $idle"), fixture.events.filter { it.startsWith("drive") })
    }

    /** 逾時處理已推進的對局，同一輪不再推進。 */
    @Test
    fun `games advanced by a timeout are not advanced again`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (timedOut, other) = fixture.games
        fixture.timedOut += timedOut

        fixture.tick()

        assertEquals(listOf("drive $other"), fixture.events.filter { it.startsWith("drive") })
    }

    /** 有待完成的胡牌或流局流程時先補完，這一輪不驅動玩家也不補做自動摸牌。 */
    @Test
    fun `pending transitions are resumed instead of driving`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(1))
        val game = fixture.games.single()
        fixture.pendingTransitions += game

        fixture.tick()

        assertEquals(listOf("resume $game"), fixture.events.filter { it.contains(game.toString()) && !it.startsWith("reconcile") })
    }

    /** 一個週期內全域處理只做一次，每局只推進一次，推進分散在不同 tick。 */
    @Test
    fun `global work runs once per cycle while games advance on their own ticks`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 20, games = games(2))

        val drivesPerTick = List(20) {
            fixture.events.clear()
            fixture.tick()
            fixture.events.filter { it.startsWith("drive") }.size to fixture.events.count { it == "synchronize" }
        }

        assertEquals(listOf(1, 1), drivesPerTick.map { it.first }.filter { it > 0 }, "Each game advances once, on its own tick.")
        assertEquals(1, drivesPerTick.sumOf { it.second }, "Timers are synchronized once per cycle.")
    }

    /** 單局推進失敗時記錄該局，其他對局照常推進。 */
    @Test
    fun `a failing game does not stop the others`() = runBlocking {
        val fixture = Fixture.registered(cycleTicks = 1, games = games(2))
        val (failing, other) = fixture.games
        fixture.failing += failing

        fixture.tick()

        assertEquals(listOf(failing), fixture.failures)
        assertEquals(listOf("drive $other"), fixture.events.filter { it.startsWith("drive") })
    }

    /** 記錄每個呼叫的假資料。 */
    private class Fixture(cycleTicks: Int, val games: List<Uuid>) {
        val events = mutableListOf<String>()
        val busy = mutableSetOf<Uuid>()
        val timedOut = mutableSetOf<Uuid>()
        val pendingTransitions = mutableSetOf<Uuid>()
        val failing = mutableSetOf<Uuid>()
        val failures = mutableListOf<Uuid>()

        val processor = DecisionTickProcessor(
            rotation = GameAdvanceRotation(cycleTicks),
            listGames = { games },
            reconcile = { gameId -> events += "reconcile $gameId" },
            settleTimeouts = {
                events += "timeouts"
                timedOut.toList()
            },
            isBusy = { gameId -> gameId in busy },
            resumeTransition = { gameId ->
                (gameId in pendingTransitions).also { if (it) events += "resume $gameId" }
            },
            drive = { gameId ->
                check(gameId !in failing) { "boom" }
                events += "drive $gameId"
            },
            autoDraw = { gameId -> events += "autoDraw $gameId" },
            synchronizeAll = { events += "synchronize" },
            onAdvanceFailed = { gameId, _ -> failures += gameId },
        )

        /** 前進一個 tick 並執行這個 tick 的處理。 */
        suspend fun tick() {
            processor.advanceTick()
            processor.process()
        }

        companion object {
            /**
             * 建立假資料並先跑一個 tick 讓對局登記到輪轉中：對局在處理時才登記，登記前的位置已標記過，因此新對局在下一次
             * 輪到它的位置才推進（一個週期內）。登記那個 tick 的呼叫紀錄會清除。
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
