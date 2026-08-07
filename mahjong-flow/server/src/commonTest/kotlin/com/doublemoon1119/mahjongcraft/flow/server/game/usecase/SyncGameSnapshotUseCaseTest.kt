package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [SyncGameSnapshotUseCase] 的單元測試類別。
 */
class SyncGameSnapshotUseCaseTest {

    private val gameId = Uuid.random()
    private val playerId = Uuid.random()

    /**
     * 測試玩家請求同步時，應正確生成針對該玩家身分的快照並存入快照倉庫。
     */
    @Test
    fun `test sync game snapshot for player correctly`() = runTest {
        val gameRepo = FakeGameRepository()
        val snapshotRepo = FakeGameSnapshotRepository()
        val useCase = SyncGameSnapshotUseCase(gameRepo, snapshotRepo)

        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        gameRepo.setTableState(table)

        val result = useCase(gameId, playerId)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val snapshot = snapshotRepo.getSnapshot(gameId, playerId)
        assertNotNull(snapshot, "A snapshot should be generated for the player.")
        assertEquals(gameId, snapshot.id)
        assertEquals(0, snapshot.currentPlayerIndex)
    }

    /**
     * 測試當對局不存在時，應回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test sync game snapshot fails when game not exists`() = runTest {
        val gameRepo = FakeGameRepository()
        val snapshotRepo = FakeGameSnapshotRepository()
        val useCase = SyncGameSnapshotUseCase(gameRepo, snapshotRepo)

        val result = useCase(gameId, playerId)

        assertTrue(result is Outcome.Error, "Expected Error but got $result")
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }
}
