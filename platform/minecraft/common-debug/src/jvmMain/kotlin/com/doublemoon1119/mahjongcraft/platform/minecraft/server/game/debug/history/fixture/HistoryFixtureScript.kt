package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import com.doublemoon1119.mahjongcraft.flow.server.game.random.RoundRandomnessSource
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.table.RoundRandomness
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollSource

/**
 * 一次產生測試牌譜時的腳本進度。
 *
 * - 提供每一局的骰子與牌山排列，並依序交出各家的決定。
 * - 腳本與實際對局不符時記下原因，不中斷對局。
 * - 產生完畢後，由 [verify] 確認整份腳本都照順序執行完。
 * - 只供單一無頭對局依序使用。
 *
 * @property fixture 這次產生的測試牌譜。
 */
class HistoryFixtureScript(private val fixture: HistoryFixture) : RoundRandomnessSource {
    /** 目前這一局在整場中的開局序號。開局前為 -1。 */
    private var roundOrdinal = -1

    /** 目前這一局各家還沒執行的決定。 */
    private var pending: Map<Wind, ArrayDeque<HistoryFixtureAction>> = emptyMap()

    /** 腳本與實際對局不符的原因。 */
    private val failures = mutableListOf<String>()

    override fun roundRandomness(matchSeed: MatchSeed, roundOrdinal: Int): RoundRandomness {
        finishRound()
        this.roundOrdinal = roundOrdinal
        val seeded = matchSeed.roundRandomness(roundOrdinal)
        val round = fixture.rounds.getOrNull(roundOrdinal) ?: return seeded.also { pending = emptyMap() }
        pending = round.steps.groupBy(HistoryFixtureStep::wind).mapValues { (_, steps) -> ArrayDeque(steps.map(HistoryFixtureStep::action)) }
        val config = fixture.scenario.ruleConfig
        return RoundRandomness(
            seatOrder = seeded.seatOrder,
            wall = seeded.wall,
            dice = ScriptedDice(round.dice.toMutableList()),
            components = seeded.components,
            wallArrangement = HistoryFixtureWallArrangement(round, config.dealBatchSizes(), config.playerCount()),
        )
    }

    /**
     * 這一家下一個還沒執行的決定。
     *
     * @param wind 這一家在這一局的自風。
     * @return 下一個決定；這一局沒有腳本或已經執行完時為 null。
     */
    fun next(wind: Wind): HistoryFixtureAction? = pending[wind]?.firstOrNull()

    /**
     * 標記這一家下一個決定已經執行。
     *
     * @param wind 這一家在這一局的自風。
     */
    fun consume(wind: Wind) {
        pending[wind]?.removeFirstOrNull()
    }

    /**
     * 記下腳本與實際對局不符的地方。
     *
     * @param reason 原因。
     */
    fun fail(reason: String) {
        failures += "Round ${roundOrdinal + 1}: $reason"
    }

    /**
     * 確認腳本全部照順序執行完，沒有不符的地方。
     *
     * @throws IllegalStateException 腳本沒有執行完或有不符的地方時拋出，訊息列出全部原因。
     */
    fun verify() {
        finishRound()
        if (roundOrdinal + 1 < fixture.rounds.size) fail("the match ended before every scripted round was played")
        check(failures.isEmpty()) { "History fixture ${fixture.id} did not follow its script: ${failures.joinToString("; ")}" }
    }

    /** 一局結束時，記下這一局還沒執行的決定。 */
    private fun finishRound() {
        pending.forEach { (wind, actions) -> if (actions.isNotEmpty()) fail("$wind did not get to ${actions.first()}") }
        pending = emptyMap()
    }

    /**
     * 依序交出指定點數的骰子。
     *
     * @property values 還沒擲出的點數。
     */
    private inner class ScriptedDice(private val values: MutableList<Int>) : DiceRollSource {
        override fun roll(diceCount: Int): DiceRollResult {
            check(values.size >= diceCount) { "History fixture ${fixture.id} has no more dice values" }
            return DiceRollResult.of(List(diceCount) { values.removeFirst() })
        }
    }
}

/**
 * 規則的人數。
 *
 * @return 這個規則固定的人數。
 */
private fun MahjongRuleConfig.playerCount(): Int {
    check(minPlayers == maxPlayers) { "History fixtures need a fixed player count" }
    return maxPlayers
}
