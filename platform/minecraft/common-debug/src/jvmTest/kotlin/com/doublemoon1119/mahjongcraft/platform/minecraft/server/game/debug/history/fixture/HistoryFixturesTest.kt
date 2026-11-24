package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRunner
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledHeadlessHistoryRegistries
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 測試牌譜照腳本打完整場，內容固定，並包含各自要涵蓋的情況。 */
class HistoryFixturesTest {
    /** 碰、立直與一發自摸依序出現在第一局，整場正常結束。 */
    @Test
    fun `pon riichi and ippatsu tsumo follow the script`() = runTest {
        val facts = play(HistoryFixtures.RIICHI_PON_RIICHI_IPPATSU_TSUMO)
        val firstRound = facts.takeWhile { it !is HistoryFact.RoundCompleted }
        val actions = firstRound.filterIsInstance<HistoryFact.ActionAccepted>().map { it.action }
        val pon = actions.indexOfFirst { it is GameAction.Pon }
        val riichi = actions.indexOfFirst { it is GameAction.Extension && it.value.id == RiichiGameAction.Riichi.id }
        assertTrue(pon >= 0 && riichi > pon, "The first round must have a pon followed by a riichi: $actions")
        assertTrue(firstRound.any { it is HistoryFact.WinSettled }, "The first round must end with a win")
        assertTrue(facts.last() == HistoryFact.ReturnedToRoom)
    }

    /** 第一局榮和，第二局沒有和牌而流局，整場正常結束。 */
    @Test
    fun `ron then exhaustive draw follow the script`() = runTest {
        val facts = play(HistoryFixtures.RIICHI_RON_THEN_EXHAUSTIVE_DRAW)
        val rounds = facts.split()
        assertTrue(rounds[0].any { it is HistoryFact.WinSettled }, "The first round must end with a ron")
        assertTrue(rounds[1].none { it is HistoryFact.WinSettled }, "The second round must end without a win")
        assertTrue(rounds[1].any { it is HistoryFact.RoundCompleted }, "The second round must complete")
    }

    /** 同一份測試牌譜每次產生的事件順序都相同。 */
    @Test
    fun `fixtures are reproducible`() = runTest {
        HistoryFixtures.ALL.forEach { fixture ->
            assertEquals(play(fixture).map(::describe), play(fixture).map(::describe), "${fixture.id} must be reproducible")
        }
    }

    /** 腳本要求的動作沒有發生時，產生完畢的檢查會失敗。 */
    @Test
    fun `an unmet script fails verification`() = runTest {
        val fixture = HistoryFixtures.RIICHI_PON_RIICHI_IPPATSU_TSUMO
        val broken = fixture.copy(
            id = "mahjongcraft:riichi/broken",
            rounds = listOf(fixture.rounds.single().copy(steps = fixture.rounds.single().steps + HistoryFixtureStep(Wind.WEST, HistoryFixtureAction.Ron))),
        )
        assertFailsWith<IllegalStateException> { play(broken) }
    }

    /**
     * 照腳本打完整場，確認腳本全部執行完。
     *
     * @param fixture 測試牌譜。
     * @return 整場依序產生的事實。
     */
    private suspend fun play(fixture: HistoryFixture): List<HistoryFact> {
        val core = bundledCoreExtensionRegistries()
        val runtime = HistoryFixtureRuntime.create(
            fixture = fixture,
            registries = bundledHeadlessHistoryRegistries(core),
            extensionActions = core.gameActionAiRegistry,
            failurePolicy = ExtensionFailurePolicy.Default,
        )
        val facts = mutableListOf<HistoryFact>()
        HeadlessHistoryMatchRunner(runtime).events().collect { progress ->
            runtime.store.acknowledgeHistoryEvents(progress.events.mapTo(mutableSetOf()) { it.matchId to it.sequence })
            facts += progress.events.map { it.fact }
        }
        runtime.verify()
        return facts
    }

    /**
     * 依局分開事實：每一局從開局或換局開始。
     *
     * @receiver 整場依序產生的事實。
     * @return 每一局的事實。
     */
    private fun List<HistoryFact>.split(): List<List<HistoryFact>> = fold(mutableListOf<MutableList<HistoryFact>>()) { rounds, fact ->
        if (fact is HistoryFact.MatchStarted || fact is HistoryFact.RoundStarted) rounds += mutableListOf<HistoryFact>()
        rounds.lastOrNull()?.add(fact)
        rounds
    }

    /**
     * 不含識別碼的事實描述，用來比較兩次產生的順序。
     *
     * @param fact 事實。
     * @return 事實種類，動作事實再加上動作種類。
     */
    private fun describe(fact: HistoryFact): String = when (fact) {
        is HistoryFact.ActionAccepted -> "${fact::class.simpleName}:${fact.action::class.simpleName}"
        else -> fact::class.simpleName.orEmpty()
    }
}
