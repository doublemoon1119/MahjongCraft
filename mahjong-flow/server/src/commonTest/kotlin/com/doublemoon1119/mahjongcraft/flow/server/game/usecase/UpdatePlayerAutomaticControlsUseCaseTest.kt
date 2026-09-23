package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [UpdatePlayerAutomaticControlsUseCase] 的原子驗證與更新測試。 */
class UpdatePlayerAutomaticControlsUseCaseTest {
    /** 測試共用的 repository、規則 registry 與 use case。 */
    private class Fixtures {
        /** 測試用對局 repository。 */
        val gameRepository = FakeGameRepository()

        /** 包含內建規則的 module registry。 */
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

        /** 本次受測 use case。 */
        val useCase = UpdatePlayerAutomaticControlsUseCase(gameRepository, moduleRegistry)

        /** 建立並保存一場四人日麻對局。 */
        suspend fun createGame(
            automaticControlRevision: Long = 0L,
            enabledControlIds: Set<String> = emptySet(),
            isMatchOver: Boolean = false,
        ): Pair<Game, Uuid> {
            val playerId = Uuid.random()
            val players = listOf(playerId, Uuid.random(), Uuid.random(), Uuid.random()).map { id ->
                FakeMahjongPlayerFactory.create(id = id)
            }
            val game = Game(
                tableState = FakeTableStateFactory.create(
                    players = players,
                    config = RiichiRuleConfig(),
                ),
                flowConfig = GameFlowConfig(),
                enabledAutomaticControlIdsByPlayerId = if (enabledControlIds.isEmpty()) {
                    emptyMap()
                } else {
                    mapOf(playerId to enabledControlIds)
                },
                automaticControlRevision = automaticControlRevision,
                isMatchOver = isMatchOver,
            )
            gameRepository.setGame(game)
            return game to playerId
        }
    }

    /** 合法變更應替換集合並只遞增一次 revision。 */
    @Test
    fun `accepted change replaces controls and increments revision`() = runTest {
        val fixtures = Fixtures()
        val (game, playerId) = fixtures.createGame(automaticControlRevision = 4L)
        val enabledIds = setOf(BuiltInAutomaticControlIds.AUTO_WIN, BuiltInAutomaticControlIds.AUTO_TSUMOGIRI)

        val result = fixtures.useCase(
            game.id,
            playerId,
            expectedRevision = 4L,
            enabledControlIds = enabledIds,
        )

        val accepted = assertIs<UpdatePlayerAutomaticControlsResult.Accepted>(result)
        assertTrue(accepted.changed)
        assertEquals(5L, accepted.snapshot.revision)
        assertEquals(enabledIds, accepted.snapshot.enabledControlIds)
        val saved = fixtures.gameRepository.getGame(game.id)
        assertEquals(5L, saved?.automaticControlRevision)
        assertEquals(enabledIds, saved?.enabledAutomaticControlIdsByPlayerId?.get(playerId))
    }

    /** 等值提交應接受但不製造新的 revision。 */
    @Test
    fun `accepted no-op keeps revision unchanged`() = runTest {
        val fixtures = Fixtures()
        val enabledIds = setOf(BuiltInAutomaticControlIds.DECLINE_CALLS)
        val (game, playerId) = fixtures.createGame(3L, enabledIds)

        val result = fixtures.useCase(
            game.id,
            playerId,
            expectedRevision = 3L,
            enabledControlIds = enabledIds,
        )

        val accepted = assertIs<UpdatePlayerAutomaticControlsResult.Accepted>(result)
        assertFalse(accepted.changed)
        assertEquals(3L, accepted.snapshot.revision)
        assertEquals(3L, fixtures.gameRepository.getGame(game.id)?.automaticControlRevision)
    }

    /** 過期 revision 應回傳最新快照且不修改權威狀態。 */
    @Test
    fun `stale revision returns current snapshot without update`() = runTest {
        val fixtures = Fixtures()
        val currentIds = setOf(BuiltInAutomaticControlIds.AUTO_WIN)
        val (game, playerId) = fixtures.createGame(7L, currentIds)

        val result = fixtures.useCase(
            game.id,
            playerId,
            expectedRevision = 6L,
            enabledControlIds = setOf(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI),
        )

        val stale = assertIs<UpdatePlayerAutomaticControlsResult.Stale>(result)
        assertEquals(7L, stale.snapshot.revision)
        assertEquals(currentIds, stale.snapshot.enabledControlIds)
        assertEquals(currentIds, fixtures.gameRepository.getGame(game.id)?.enabledAutomaticControlIdsByPlayerId?.get(playerId))
    }

    /** 目前規則未支援的合法 ID 應整批拒絕。 */
    @Test
    fun `unsupported control rejects entire update`() = runTest {
        val fixtures = Fixtures()
        val (game, playerId) = fixtures.createGame()

        val result = fixtures.useCase(game.id, playerId, 0L, setOf("example:auto_action"))

        val rejected = assertIs<UpdatePlayerAutomaticControlsResult.Rejected>(result)
        assertEquals(AutomaticControlUpdateRejection.UNSUPPORTED_CONTROL_ID, rejected.reason)
        assertTrue(fixtures.gameRepository.getGame(game.id)?.enabledAutomaticControlIdsByPlayerId.isNullOrEmpty())
    }

    /** 非 namespaced ID 應在建立 Game 前被拒絕，不讓 domain constructor 拋出例外。 */
    @Test
    fun `invalid control id is rejected without mutation`() = runTest {
        val fixtures = Fixtures()
        val (game, playerId) = fixtures.createGame()

        val result = fixtures.useCase(game.id, playerId, 0L, setOf("auto_win"))

        val rejected = assertIs<UpdatePlayerAutomaticControlsResult.Rejected>(result)
        assertEquals(AutomaticControlUpdateRejection.INVALID_CONTROL_ID, rejected.reason)
        assertTrue(fixtures.gameRepository.getGame(game.id)?.enabledAutomaticControlIdsByPlayerId.isNullOrEmpty())
    }

    /** 非對局成員不得取得個人快照或修改其他玩家狀態。 */
    @Test
    fun `non-member is rejected without snapshot`() = runTest {
        val fixtures = Fixtures()
        val (game, _) = fixtures.createGame()

        val result = fixtures.useCase(game.id, Uuid.random(), 0L, emptySet())

        val rejected = assertIs<UpdatePlayerAutomaticControlsResult.Rejected>(result)
        assertEquals(AutomaticControlUpdateRejection.PLAYER_NOT_IN_GAME, rejected.reason)
        assertNull(rejected.snapshot)
    }

    /** 每位成員只取得自己的控制集合，另一位玩家的變更不得混入快照。 */
    @Test
    fun `member snapshots expose only their own enabled controls`() = runTest {
        val fixtures = Fixtures()
        val (game, firstPlayerId) = fixtures.createGame()
        val secondPlayerId = game.tableState.players.first { it.id != firstPlayerId }.id

        val first = assertIs<UpdatePlayerAutomaticControlsResult.Accepted>(
            fixtures.useCase(game.id, firstPlayerId, 0L, setOf(BuiltInAutomaticControlIds.AUTO_WIN)),
        )
        val second = assertIs<UpdatePlayerAutomaticControlsResult.Accepted>(
            fixtures.useCase(game.id, secondPlayerId, 1L, setOf(BuiltInAutomaticControlIds.DECLINE_CALLS)),
        )
        val firstNoOp = assertIs<UpdatePlayerAutomaticControlsResult.Accepted>(
            fixtures.useCase(game.id, firstPlayerId, 2L, setOf(BuiltInAutomaticControlIds.AUTO_WIN)),
        )

        assertEquals(setOf(BuiltInAutomaticControlIds.AUTO_WIN), first.snapshot.enabledControlIds)
        assertEquals(setOf(BuiltInAutomaticControlIds.DECLINE_CALLS), second.snapshot.enabledControlIds)
        assertEquals(setOf(BuiltInAutomaticControlIds.AUTO_WIN), firstNoOp.snapshot.enabledControlIds)
        assertEquals(2L, firstNoOp.snapshot.revision)
    }

    /** 不存在或已結束的對局不得接受更新。 */
    @Test
    fun `unavailable games reject updates`() = runTest {
        val fixtures = Fixtures()
        val missing = fixtures.useCase(Uuid.random(), Uuid.random(), 0L, emptySet())
        assertEquals(
            AutomaticControlUpdateUnavailableReason.GAME_NOT_FOUND,
            assertIs<UpdatePlayerAutomaticControlsResult.Unavailable>(missing).reason,
        )

        val (finishedGame, playerId) = fixtures.createGame(isMatchOver = true)
        val finished = fixtures.useCase(finishedGame.id, playerId, 0L, emptySet())
        assertEquals(
            AutomaticControlUpdateUnavailableReason.MATCH_OVER,
            assertIs<UpdatePlayerAutomaticControlsResult.Unavailable>(finished).reason,
        )
    }
}
