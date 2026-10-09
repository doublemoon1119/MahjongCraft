package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 驗證提交事實通知在 JVM 真實並發下的鎖邊界、順序與取消行為。 */
class AuthoritativeStateStoreConcurrencyTest {
    /** 第一筆提交的 listener 被阻塞時，第二筆交易不得進入狀態更新區塊。 */
    @Test
    fun `second transaction waits for first committed callback`() = runBlocking {
        val store = AuthoritativeStateStore()
        val tableState = FakeTableStateFactory.create()
        val repository = GameRepositoryImpl(store)
        repository.setTableState(tableState)
        val committed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondAttempted = CountDownLatch(1)
        val secondBlockEntered = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        store.setCommittedFactsListener(
            object : CommittedFactsListener {
                override fun onCommitted(sequence: Long, facts: CommittedGameFacts) {
                    committed.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release the first callback" }
                }

                override fun onReleased(sequence: Long) = Unit
            },
        )

        val first = scope.async {
            update(repository, tableState.id, 1L)
        }
        try {
            assertTrue(committed.await(5, TimeUnit.SECONDS))
            val second = scope.async {
                secondAttempted.countDown()
                repository.updateGame(
                    gameId = tableState.id,
                    history = { _, _, _ -> listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)) },
                ) { current ->
                    secondBlockEntered.countDown()
                    current!!.copy(automaticControlRevision = 2L) to Unit
                }
            }

            assertTrue(secondAttempted.await(5, TimeUnit.SECONDS))
            assertFalse(secondBlockEntered.await(100, TimeUnit.MILLISECONDS))
            release.countDown()
            first.await()
            second.await()
            assertTrue(secondBlockEntered.await(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            scope.cancel()
        }
    }

    /** 釋放通知可在不死結的情況下從 store 讀取已提交狀態。 */
    @Test
    fun `released callback can read the committed snapshot`() = runBlocking {
        val store = AuthoritativeStateStore()
        val tableState = FakeTableStateFactory.create()
        val repository = GameRepositoryImpl(store)
        repository.setTableState(tableState)
        val released = CompletableDeferred<AuthoritativeStateSnapshot>()
        store.setCommittedFactsListener(
            object : CommittedFactsListener {
                override fun onCommitted(sequence: Long, facts: CommittedGameFacts) = Unit

                override fun onReleased(sequence: Long) {
                    released.complete(runBlocking { withTimeout(5.seconds) { store.snapshot() } })
                }
            },
        )

        update(repository, tableState.id, 1L)

        val snapshot = withTimeout(5.seconds) { released.await() }
        assertTrue(snapshot.games.getValue(tableState.id).automaticControlRevision == 1L)
    }

    /** 提交 callback 取消目前協程後，已提交狀態仍保留且釋放通知仍會送出。 */
    @Test
    fun `release callback runs when the submitting coroutine is cancelled after commit`() = runBlocking {
        val store = AuthoritativeStateStore()
        val tableState = FakeTableStateFactory.create()
        val repository = GameRepositoryImpl(store)
        repository.setTableState(tableState)
        val released = CountDownLatch(1)
        lateinit var submittingJob: Job
        store.setCommittedFactsListener(
            object : CommittedFactsListener {
                override fun onCommitted(sequence: Long, facts: CommittedGameFacts) {
                    submittingJob.cancel()
                }

                override fun onReleased(sequence: Long) {
                    released.countDown()
                }
            },
        )

        submittingJob = launch(start = CoroutineStart.LAZY) {
            runCatching { update(repository, tableState.id, 1L) }
        }
        submittingJob.start()
        submittingJob.join()

        assertTrue(released.await(5, TimeUnit.SECONDS))
        assertEquals(1L, store.getGame(tableState.id)!!.automaticControlRevision)
    }

    /**
     * 以單次歷史事實更新測試對局。
     *
     * @param repository 欲更新的遊戲儲存庫。
     * @param gameId 欲更新的對局 ID。
     * @param revision 寫入的自動操作 revision。
     */
    private suspend fun update(
        repository: GameRepositoryImpl,
        gameId: Uuid,
        revision: Long,
    ) {
        repository.updateGame(
            gameId = gameId,
            history = { _, _, _ -> listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)) },
        ) { current ->
            current!!.copy(automaticControlRevision = revision) to Unit
        }
    }
}
