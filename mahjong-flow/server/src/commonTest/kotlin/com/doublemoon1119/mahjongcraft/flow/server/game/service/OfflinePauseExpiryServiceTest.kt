package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** [OfflinePauseExpiryService] 依暫停時限中止對局的測試。 */
class OfflinePauseExpiryServiceTest {
    /** 驗證暫停累計時間到達時限的對局以離線原因中止，未到時限的不受影響。 */
    @Test
    fun `test abort expired aborts only matches whose pause reached the limit`() = runTest {
        val repository = FakeGameRepository()
        val expired = game(offlinePauseElapsed = 10.minutes)
        val waiting = game(offlinePauseElapsed = 9.minutes)
        repository.setGame(expired)
        repository.setGame(waiting)
        val aborted = mutableListOf<Pair<Game, String>>()
        val service = OfflinePauseExpiryService(
            gameRepository = repository,
            settings = settings(offlinePauseLimit = 10.minutes),
            abortHandler = { game, reasonId, _ ->
                aborted += game to reasonId
                true
            },
        )

        service.abortExpired()

        assertEquals(listOf(expired to GameAbortReasonIds.PLAYERS_OFFLINE), aborted)
    }

    /** 驗證時限為 0 時一直等待，不中止任何對局。 */
    @Test
    fun `test abort expired waits forever when the limit is zero`() = runTest {
        val repository = FakeGameRepository()
        repository.setGame(game(offlinePauseElapsed = 100.minutes))
        val aborted = mutableListOf<Game>()
        val service = OfflinePauseExpiryService(
            gameRepository = repository,
            settings = settings(offlinePauseLimit = Duration.ZERO),
            abortHandler = { game, _, _ ->
                aborted += game
                true
            },
        )

        service.abortExpired()

        assertEquals(emptyList(), aborted)
    }

    /** 驗證已經結束的對局不再中止。 */
    @Test
    fun `test abort expired skips matches that are already over`() = runTest {
        val repository = FakeGameRepository()
        repository.setGame(game(offlinePauseElapsed = 10.minutes).copy(isMatchOver = true, matchEndReasonId = "test:ended"))
        val aborted = mutableListOf<Game>()
        val service = OfflinePauseExpiryService(
            gameRepository = repository,
            settings = settings(offlinePauseLimit = 1.minutes),
            abortHandler = { game, _, _ ->
                aborted += game
                true
            },
        )

        service.abortExpired()

        assertEquals(emptyList(), aborted)
    }

    /**
     * 建立只有一位真人的對局。
     *
     * @param offlinePauseElapsed 已累計的暫停時間。
     * @return 測試對局。
     */
    private fun game(offlinePauseElapsed: Duration): Game = Game(
        tableState = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create())),
        flowConfig = GameFlowConfig(),
        offlinePauseElapsed = offlinePauseElapsed,
    )

    /**
     * 建立只設定暫停時限的設定。
     *
     * @param offlinePauseLimit 暫停累計時間上限。
     * @return 測試用設定。
     */
    private fun settings(offlinePauseLimit: Duration): MatchAbsenceSettings = object : MatchAbsenceSettings {
        override val inactiveRoundLimit: Int = 0
        override val offlinePauseLimit: Duration = offlinePauseLimit
    }
}
