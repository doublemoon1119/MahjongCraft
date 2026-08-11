package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [PlayerDecisionTimerFactory] 的單元測試。 */
class PlayerDecisionTimerFactoryTest {
    /** 驗證 factory 使用目前時間、流程設定與權威剩餘 B 建立計時器。 */
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
                timeControl = ActionTimeControl.Custom(actionSeconds = 7, reserveSeconds = 30),
            ),
            remainingReserveMillisByPlayerId = players.associate { player ->
                player.id to if (player.id == playerId) 12_345L else 30_000L
            },
        )
        val factory = PlayerDecisionTimerFactory(FakeMonotonicClock(nowMillis = 98_765L))

        val timer = factory.create(game, playerId)

        assertEquals(playerId, timer.playerId)
        assertEquals(98_765L, timer.startedAtMillis)
        assertEquals(7_000L, timer.actionDurationMillis)
        assertEquals(12_345L, timer.reserveAtStartMillis)
    }
}

/** 測試用可控單調時間來源。 */
private class FakeMonotonicClock(
    /** [nowMillis] 回傳的固定毫秒數。 */
    private val nowMillis: Long,
) : MonotonicClock {
    /** 回傳測試指定的固定時間。 */
    override fun nowMillis(): Long = nowMillis
}
