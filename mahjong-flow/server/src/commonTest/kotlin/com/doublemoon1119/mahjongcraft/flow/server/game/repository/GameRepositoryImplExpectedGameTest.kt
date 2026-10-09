package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** [GameRepositoryImpl.withExpectedGame] 的條件式寫入。 */
class GameRepositoryImplExpectedGameTest {
    private val repository = GameRepositoryImpl(AuthoritativeStateStore())

    /** 權威遊戲仍是預期的遊戲時，命令的寫入照常提交並回傳命令的結果。 */
    @Test
    fun `a command on the expected game is applied`() = runTest {
        val game = createGame()

        val result = repository.withExpectedGame(game.id, game) { repository.updateGame(game.id) { it?.copy(isMatchOver = true) to "done" } }

        assertEquals(ExpectedGameResult.Applied("done"), result)
        assertEquals(true, repository.getGame(game.id)?.isMatchOver)
    }

    /** 權威遊戲已改變時，命令不寫入、修改區塊不執行，結果為過期。 */
    @Test
    fun `a command on a changed game is not written`() = runTest {
        val game = createGame()
        repository.updateGame(game.id) { it?.copy(automaticControlRevision = 1) to Unit }
        var blockRan = false

        val result = repository.withExpectedGame(game.id, game) {
            repository.updateGame(game.id) {
                blockRan = true
                it?.copy(isMatchOver = true) to Unit
            }
        }

        assertEquals(ExpectedGameResult.Stale, result)
        assertEquals(false, blockRan)
        assertEquals(false, repository.getGame(game.id)?.isMatchOver)
    }

    /** 命令攔下了過期的例外也仍然是過期，之後對這一局的寫入同樣被拒絕。 */
    @Test
    fun `a swallowed stale write stays stale`() = runTest {
        val game = createGame()
        repository.updateGame(game.id) { it?.copy(automaticControlRevision = 1) to Unit }

        val result = repository.withExpectedGame(game.id, game) {
            runCatching { repository.updateGame(game.id) { it?.copy(isMatchOver = true) to Unit } }
            runCatching { repository.updateGame(game.id) { it?.copy(isMatchOver = true) to Unit } }
        }

        assertEquals(ExpectedGameResult.Stale, result)
        assertEquals(false, repository.getGame(game.id)?.isMatchOver)
    }

    /** 範圍內對同一局的第二次寫入違反契約：第一次寫入已經提交，不回滾。 */
    @Test
    fun `a second write to the game breaks the contract`() = runTest {
        val game = createGame()

        assertFailsWith<ExpectedGameWrittenTwiceException> {
            repository.withExpectedGame(game.id, game) {
                repository.updateGame(game.id) { it?.copy(automaticControlRevision = 1) to Unit }
                repository.updateGame(game.id) { it?.copy(isMatchOver = true) to Unit }
            }
        }
        val current = assertNotNull(repository.getGame(game.id))
        assertEquals(1, current.automaticControlRevision)
        assertEquals(false, current.isMatchOver)
    }

    /** 命令攔下第二次寫入的例外並正常返回時，範圍結束仍丟出違約例外；第一次寫入維持提交，不回滾。 */
    @Test
    fun `a swallowed second write still breaks the contract`() = runTest {
        val game = createGame()

        assertFailsWith<ExpectedGameWrittenTwiceException> {
            repository.withExpectedGame(game.id, game) {
                repository.updateGame(game.id) { it?.copy(automaticControlRevision = 1) to Unit }
                runCatching { repository.updateGame(game.id) { it?.copy(isMatchOver = true) to Unit } }
                "completed"
            }
        }
        val current = assertNotNull(repository.getGame(game.id))
        assertEquals(1, current.automaticControlRevision)
        assertEquals(false, current.isMatchOver)
    }

    /** 其他遊戲的寫入不受範圍限制，也不算在這一局的寫入次數內。 */
    @Test
    fun `writes to other games are not restricted`() = runTest {
        val game = createGame()
        val other = createGame()
        repository.updateGame(other.id) { it?.copy(automaticControlRevision = 5) to Unit }

        val result = repository.withExpectedGame(game.id, game) {
            repository.updateGame(other.id) { it?.copy(isMatchOver = true) to Unit }
            repository.updateGame(game.id) { it?.copy(isMatchOver = true) to Unit }
        }

        assertIs<ExpectedGameResult.Applied<Unit>>(result)
        assertEquals(true, repository.getGame(other.id)?.isMatchOver)
        assertEquals(true, repository.getGame(game.id)?.isMatchOver)
    }

    /** 範圍結束後的寫入不受限制；範圍不能巢狀使用。 */
    @Test
    fun `the scope covers only the command`() = runTest {
        val game = createGame()

        repository.withExpectedGame(game.id, game) { repository.updateGame(game.id) { it?.copy(automaticControlRevision = 1) to Unit } }
        repository.updateGame(game.id) { it?.copy(isMatchOver = true) to Unit }
        assertEquals(true, repository.getGame(game.id)?.isMatchOver)

        val current = assertNotNull(repository.getGame(game.id))
        assertFailsWith<IllegalArgumentException> {
            repository.withExpectedGame(game.id, current) { repository.withExpectedGame(game.id, current) { Unit } }
        }
    }

    /** 移除遊戲同樣受範圍限制。 */
    @Test
    fun `removing a changed game is not written`() = runTest {
        val game = createGame()
        repository.updateGame(game.id) { it?.copy(automaticControlRevision = 1) to Unit }

        val result = repository.withExpectedGame(game.id, game) { repository.removeTableState(game.id) }

        assertEquals(ExpectedGameResult.Stale, result)
        assertNotNull(repository.getGame(game.id))
    }

    private suspend fun createGame(): Game {
        val table = FakeTableStateFactory.create(
            id = Uuid.random(),
            players = listOf(FakeMahjongPlayerFactory.create()),
            config = RiichiRuleConfig(),
        )
        val game = Game(table, GameFlowConfig())
        repository.updateGame(table.id) { game to Unit }
        return game
    }
}
