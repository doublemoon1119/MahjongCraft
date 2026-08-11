package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DecisionTimeStatus
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdate
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [DecisionTimerSynchronizationService] 的目標玩家與停止同步測試。 */
class DecisionTimerSynchronizationServiceTest {
    /** 驗證有效計時只同步給真人玩家，不傳給 AI。 */
    @Test
    fun `test synchronize publishes active status only to human players`() = runTest {
        val fixtures = Fixtures()
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    FakeMahjongPlayerFactory.create(id = humanId),
                    FakeMahjongPlayerFactory.create(id = aiId, aiStrategyKey = "test"),
                ),
            ),
            flowConfig = GameFlowConfig(),
        )
        fixtures.repository.setGame(game)
        val statuses = mapOf(
            humanId to status(humanId),
            aiId to status(aiId),
        )

        fixtures.service.synchronize(game.id, statuses)

        assertEquals(
            listOf<Pair<Uuid, DecisionTimerUpdate>>(
                humanId to DecisionTimerUpdate.Active(
                    game.id,
                    PlayerDecisionPhase.OWN_TURN,
                    4_000L,
                    20_000L,
                ),
            ),
            fixtures.publisher.updates,
        )
    }

    /** 驗證先前取得決策權的玩家離開 active 狀態後會收到停止更新。 */
    @Test
    fun `test synchronize publishes stopped update after decision ends`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            ),
            flowConfig = GameFlowConfig(),
        )
        fixtures.repository.setGame(game)
        fixtures.service.synchronize(game.id, mapOf(playerId to status(playerId)))
        fixtures.publisher.updates.clear()

        fixtures.service.synchronize(game.id, emptyMap())

        assertEquals(
            listOf<Pair<Uuid, DecisionTimerUpdate>>(playerId to DecisionTimerUpdate.Stopped(game.id)),
            fixtures.publisher.updates,
        )
    }

    /** 建立測試使用的權威倉庫、timer manager、publisher 與同步服務。 */
    private class Fixtures {
        /** 測試權威遊戲倉庫。 */
        val repository = FakeGameRepository()

        /** 紀錄所有同步更新。 */
        val publisher = FakeDecisionTimerUpdatePublisher()

        /** 受測同步服務。 */
        val service: DecisionTimerSynchronizationService

        init {
            val clock = FixedSynchronizationClock()
            val manager = GameDecisionTimerManager(
                repository,
                GameDecisionAuthorityResolver(),
                PlayerDecisionTimerFactory(clock),
                clock,
            )
            service = DecisionTimerSynchronizationService(manager, repository, publisher)
        }
    }

    private companion object {
        /** 建立固定剩餘時間的 active decision status。 */
        fun status(playerId: Uuid) = ActivePlayerDecisionStatus(
            playerId,
            PlayerDecisionPhase.OWN_TURN,
            DecisionTimeStatus(4_000L, 20_000L, false),
        )
    }
}

/** 同步服務測試不需推進的固定單調時間。 */
private class FixedSynchronizationClock : MonotonicClock {
    /** 永遠回傳 session 起點。 */
    override fun nowMillis(): Long = 0L
}
