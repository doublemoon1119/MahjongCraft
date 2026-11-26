package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionTimer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [PlayerDecisionTimerFactory] 的單元測試。 */
class PlayerDecisionTimerFactoryTest {
    /** 驗證 factory 使用目前時間、流程設定與權威剩餘保留思考時間 建立計時器。 */
    @Test
    fun `test factory creates timer from authoritative game state`() {
        val playerId = Uuid.random()
        val players = Wind.entries.map { wind ->
            FakeMahjongPlayerFactory.create(
                initialSeat = wind,
                id = if (wind == Wind.EAST) playerId else Uuid.random(),
            )
        }
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = players,
            ),
            flowConfig = GameFlowConfig(
                timeControl = ActionTimeControl.Custom(base = 7.seconds, reserve = 30.seconds),
            ),
            remainingReserveByPlayerId = players.associate { player ->
                player.id to if (player.id == playerId) 12_345.milliseconds else 30_000.milliseconds
            },
        )
        val factory = PlayerDecisionTimerFactory(FakeMonotonicClock(now = 98_765.milliseconds))

        val timer = factory.create(game, playerId, phase = PlayerDecisionPhase.OWN_TURN)

        assertEquals(playerId, timer.playerId)
        assertEquals(98_765.milliseconds, timer.startedAt)
        assertEquals(7_000.milliseconds, timer.baseDuration)
        assertEquals(12_345.milliseconds, timer.reserveAtStart)
    }

    /**
     * 驗證 [PlayerDecisionPhase.RULE_DECISION] 的基本思考秒數採用規則選擇設定
     * 而非一般 [GameFlowConfig.timeControl]，且完全不動用共用保留思考時間池——即使玩家池子裡還有剩，
     * 這個階段建立的計時器 [PlayerDecisionTimer.reserveAtStart] 也固定是 0，用完基本思考時間就
     * 直接逾時。
     */
    @Test
    fun `test factory uses rule decision base seconds and zero reserve for rule decision phase`() {
        val playerId = Uuid.random()
        val players = Wind.entries.map { wind ->
            FakeMahjongPlayerFactory.create(
                initialSeat = wind,
                id = if (wind == Wind.EAST) playerId else Uuid.random(),
            )
        }
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = players,
            ),
            flowConfig = GameFlowConfig(
                timeControl = ActionTimeControl.Custom(base = 7.seconds, reserve = 30.seconds),
                ruleDecision = RuleDecisionConfig(45.seconds),
            ),
            remainingReserveByPlayerId = players.associate { player -> player.id to 12_000.milliseconds },
        )
        val factory = PlayerDecisionTimerFactory(FakeMonotonicClock(now = 98_765.milliseconds))

        val timer = factory.create(game, playerId, phase = PlayerDecisionPhase.RULE_DECISION)

        assertEquals(45_000.milliseconds, timer.baseDuration)
        assertEquals(0.milliseconds, timer.reserveAtStart)
    }

    /** 比照一般動作計時的規則選擇使用一般基本時間，並保留玩家尚未用完的共用保留時間。 */
    @Test
    fun `test factory uses action timing for rule action phase`() {
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId)
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(player)),
            flowConfig = GameFlowConfig(
                timeControl = ActionTimeControl.Custom(base = 7.seconds, reserve = 30.seconds),
            ),
            remainingReserveByPlayerId = mapOf(playerId to 12_000.milliseconds),
        )

        val timer = PlayerDecisionTimerFactory(FakeMonotonicClock(now = Duration.ZERO)).create(
            game = game,
            playerId = playerId,
            phase = PlayerDecisionPhase.RULE_ACTION,
        )

        assertEquals(7_000.milliseconds, timer.baseDuration)
        assertEquals(12_000.milliseconds, timer.reserveAtStart)
    }

    @Test
    fun `test factory rejects player rule decision without timing configuration`() {
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId)
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(player)),
            flowConfig = GameFlowConfig(),
            remainingReserveByPlayerId = mapOf(playerId to 12_000.milliseconds),
        )

        assertFailsWith<IllegalArgumentException> {
            PlayerDecisionTimerFactory(FakeMonotonicClock(now = Duration.ZERO)).create(
                game = game,
                playerId = playerId,
                phase = PlayerDecisionPhase.RULE_DECISION,
            )
        }
    }
}

/**
 * 測試用固定單調時間來源。
 *
 * @property now 回傳的固定毫秒數。
 */
private class FakeMonotonicClock(
    private val now: Duration,
) : MonotonicClock {
    /** 回傳測試指定的固定時間。 */
    override fun now(): Duration = now
}
