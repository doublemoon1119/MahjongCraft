package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRunner
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledHeadlessHistoryRegistries
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

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

    /** 第一局依序出現暗槓、碰、吃、加槓與明槓，最後以嶺上牌自摸。 */
    @Test
    fun `calls and kans follow the script`() = runTest {
        val firstRound = play(HistoryFixtures.RIICHI_CALLS_AND_KANS).split().first()
        val calls = firstRound.filterIsInstance<HistoryFact.ActionAccepted>().map { it.action }.mapNotNull { action ->
            when (action) {
                is GameAction.Kan -> action.type.name
                is GameAction.Pon -> "PON"
                is GameAction.Chi -> "CHI"
                else -> null
            }
        }
        assertEquals(listOf("CLOSED_KAN", "PON", "CHI", "ADDED_KAN", "OPEN_KAN"), calls)
        val win = firstRound.filterIsInstance<HistoryFact.WinSettled>().single()
        assertTrue(win.responsiblePlayerIds.isEmpty(), "The kan caller must win by self-draw")
    }

    /** 第一局一炮雙響，第二局九種九牌流局，第三局包牌者負責自摸的大三元並被擊飛，整場結束。 */
    @Test
    fun `double ron kyuushu kyuuhai and pao follow the script`() = runTest {
        val facts = play(HistoryFixtures.RIICHI_DOUBLE_RON_PAO_KYUUSHU)
        val rounds = facts.split()
        assertEquals(3, rounds.size)
        assertEquals(2, rounds[0].filterIsInstance<HistoryFact.WinSettled>().single().winDetails.size, "The first round must have two winners")
        val kyuushu = rounds[1].filterIsInstance<HistoryFact.RoundCompleted>().single().summary
        assertEquals(RiichiExhaustiveDrawReason.KyuushuKyuuhai.id, kyuushu.outcomeId)
        assertEquals(RoundCompletionClassification.ABORTIVE_DRAW, kyuushu.classification)
        val before = kyuushu.settledScoresByPlayerId
        val pao = rounds[2].filterIsInstance<HistoryFact.RoundCompleted>().single().summary
        val payers = pao.settledScoresByPlayerId.filter { (playerId, score) -> score < before.getValue(playerId) }
        assertEquals(1, payers.size, "Only the pao player pays for the self-drawn win: $before -> ${pao.settledScoresByPlayerId}")
        assertTrue(facts.any { it is HistoryFact.MatchCompleted }, "The bust must complete the match")
    }

    /** 三人麻將第一局拔北後由另一家自摸。 */
    @Test
    fun `pull north and three player tsumo follow the script`() = runTest {
        val firstRound = play(HistoryFixtures.THREE_PLAYER_RIICHI_PULL_NORTH_TSUMO).split().first()
        val actions = firstRound.filterIsInstance<HistoryFact.ActionAccepted>().map { it.action }
        assertTrue(actions.any { it is GameAction.Extension && it.value.id == RiichiGameAction.PullNorth.id }, "The first round must have a pull north: $actions")
        val win = firstRound.filterIsInstance<HistoryFact.WinSettled>().single()
        assertTrue(win.responsiblePlayerIds.isEmpty(), "The first round must end with a self-drawn win")
    }

    /** 台灣麻將依序補花後自摸、榮和、流局連莊，之後兩局換莊打完整場。 */
    @Test
    fun `taiwan flowers tsumo ron and draw follow the script`() = runTest {
        val rounds = play(HistoryFixtures.TAIWAN_FLOWERS_TSUMO_RON_DRAW).split()
        assertEquals(5, rounds.size)
        assertTrue(rounds[0].any { it is HistoryFact.TileReplacementRoundResolved }, "The first round must replace the dealt flower")
        val drawnFlowers = rounds[0].filterIsInstance<HistoryFact.ActionAccepted>().count { (it.action as? GameAction.Extension)?.value?.id == TaiwanGameAction.RevealFlower.id }
        assertEquals(1, drawnFlowers, "The first round must reveal the drawn flower")
        assertTrue(rounds[0].filterIsInstance<HistoryFact.WinSettled>().single().responsiblePlayerIds.isEmpty(), "The first round must end with a self-drawn win")
        assertTrue(rounds[1].filterIsInstance<HistoryFact.WinSettled>().single().responsiblePlayerIds.isNotEmpty(), "The second round must end with a ron")
        val draw = rounds[2].filterIsInstance<HistoryFact.RoundCompleted>().single().summary
        assertEquals(RoundCompletionClassification.EXHAUSTIVE_DRAW, draw.classification)
        assertEquals(RoundTransitionDirective.RepeatDealer, draw.transitionDirective)
    }

    /** 台灣麻將第一局八仙過海、第二局七搶一，之後兩局換莊打完整場。 */
    @Test
    fun `taiwan eight flowers and robbing follow the script`() = runTest {
        val rounds = play(HistoryFixtures.TAIWAN_EIGHT_FLOWERS_AND_ROBBING).split()
        assertEquals(4, rounds.size)
        rounds.take(2).forEachIndexed { index, round ->
            val won = round.any { it is HistoryFact.WinSettled || (it is HistoryFact.RuleEffectResolved && it.winDetails.isNotEmpty()) }
            assertTrue(won, "Round ${index + 1} must end with a flower win")
        }
    }

    /**
     * 同一份測試牌譜每次產生的事件順序都相同。
     *
     * 每份測試牌譜都完整打兩遍，工作量是其他測試的數倍。忙碌的 CI 機器上會超過預設的 60 秒時限，因此放寬時限。
     */
    @Test
    fun `fixtures are reproducible`() = runTest(timeout = 5.minutes) {
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
