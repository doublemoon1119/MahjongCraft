package com.doublemoon1119.mahjongcraft.flow.server.observer

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.HandReadinessVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證觀察者內容只在改變時送出，且房間與對局互相轉換時不中斷。 */
class ObserverSnapshotBroadcasterTest {
    /** 記錄每次送出的目標與內容。 */
    private open class RecordingSender : ObserverSnapshotSender {
        /** 依送出順序保存的目標 ID、觀察者 ID 與快照。 */
        val sent = mutableListOf<Triple<Uuid, Uuid, ObserverSnapshot?>>()

        /** 依送出順序保存的場地失效通知。 */
        val invalidated = mutableListOf<Pair<Uuid, Uuid>>()

        override suspend fun send(id: Uuid, observerId: Uuid, snapshot: ObserverSnapshot?) {
            sent += Triple(id, observerId, snapshot)
        }

        override suspend fun invalidateVenue(
            id: Uuid,
            observerId: Uuid,
            requestId: Uuid,
        ) {
            invalidated += id to observerId
        }
    }

    /** 在第一次送出時暫停的替身，用來驗證失效登記不會等待正在進行的送出。 */
    private class BlockingSender : RecordingSender() {
        val sendStarted = CompletableDeferred<Unit>()
        val releaseSend = CompletableDeferred<Unit>()

        override suspend fun send(
            id: Uuid,
            observerId: Uuid,
            snapshot: ObserverSnapshot?,
        ) {
            sendStarted.complete(Unit)
            releaseSend.await()
            super.send(id, observerId, snapshot)
        }
    }

    /** 只暫停第一位觀察者，用來重現同一場地來源在多位觀察者之間被替換的競態。 */
    private class FirstSendBlockingSender : RecordingSender() {
        val firstSendStarted = CompletableDeferred<Unit>()
        val releaseFirstSend = CompletableDeferred<Unit>()
        private var sendCount = 0

        override suspend fun send(
            id: Uuid,
            observerId: Uuid,
            snapshot: ObserverSnapshot?,
        ) {
            if (sendCount++ == 0) {
                firstSendStarted.complete(Unit)
                releaseFirstSend.await()
            }
            super.send(id, observerId, snapshot)
        }
    }

    /** 由測試直接設定的觀察者來源。 */
    private open class MutableAudienceSource : ObserverAudienceSource {
        /** 依場地 ID 保存的觀察者識別碼集合。 */
        var observers: Map<Uuid, Set<Uuid>> = emptyMap()

        override suspend fun observersById(): Map<Uuid, Set<Uuid>> = observers
    }

    /** 在觀察者來源查詢時暫停的替身，用來控制狀態在查詢期間更新的時序。 */
    private class BlockingAudienceSource : MutableAudienceSource() {
        val lookupStarted = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()

        override suspend fun observersById(): Map<Uuid, Set<Uuid>> {
            lookupStarted.complete(Unit)
            releaseLookup.await()
            return observers
        }
    }

    /** 測試中 Room 與 Game 共用的場地識別碼。 */
    private val id = Uuid.random()

    /** 測試房主及遊戲主持者的玩家識別碼。 */
    private val hostId = Uuid.random()

    /** 不屬於房間成員的測試觀察者識別碼。 */
    private val observerId = Uuid.random()

    /**
     * 建立包含一名已準備 AI 玩家的測試房間。
     *
     * @param aiStrategyKey 測試 AI 使用的策略識別碼。
     * @param aiId 測試 AI 的玩家識別碼。
     * @return 包含房主及 AI 玩家的測試房間。
     */
    private fun room(aiStrategyKey: String = "mahjongcraft:random", aiId: Uuid = Uuid.random()) = Room(
        id = id,
        hostId = hostId,
        gameConfig = GameConfig(RiichiRuleConfig()),
        playerIds = listOf(hostId, aiId),
        readyPlayerIds = listOf(aiId),
        aiPlayerStrategyKeys = mapOf(aiId to aiStrategyKey),
    )

    /**
     * 建立由房主控制、含兩名玩家的測試對局。
     *
     * @return 以測試場地 ID 建立、由房主控制的對局。
     */
    private fun game() = Game(
        tableState = FakeTableStateFactory.create(
            id = id,
            players = listOf(
                FakeMahjongPlayerFactory.create(seatWind = Wind.EAST, id = hostId),
                FakeMahjongPlayerFactory.create(seatWind = Wind.SOUTH),
            ),
            config = RiichiRuleConfig(),
        ),
        flowConfig = GameConfig(RiichiRuleConfig()).flowConfig,
        hostId = hostId,
    )

    /**
     * 在測試 store 中以 Room 取代同 ID 的 Game。
     *
     * @param room 要保存的房間；為 null 時移除同 ID 的房間。
     */
    private suspend fun AuthoritativeStateStore.setRoom(room: Room?) = update { state ->
        AuthoritativeStateUpdate(
            state = state.copy(
                rooms = if (room == null) state.rooms - id else state.rooms + (id to room),
                games = state.games - id,
            ),
            result = Unit,
        )
    }

    /**
     * 在測試 store 中以 Game 取代同 ID 的 Room。
     *
     * @param game 要保存的對局。
     */
    private suspend fun AuthoritativeStateStore.setGame(game: Game) = update { state ->
        AuthoritativeStateUpdate(
            state = state.copy(rooms = state.rooms - id, games = state.games + (id to game)),
            result = Unit,
        )
    }

    /**
     * 組成觀察者快照廣播器及其測試用 audience、sender。
     *
     * @param store 權威狀態來源。
     * @param audience 可直接設定觀察者清單的來源。
     * @param sender 記錄送出快照的替身。
     * @return 使用測試替身的觀察者快照廣播器。
     */
    private fun <T : MutableAudienceSource> broadcaster(
        store: AuthoritativeStateStore,
        audience: T,
        sender: RecordingSender,
    ) = ObserverSnapshotBroadcaster(
        store = store,
        visibilityPolicy = GameVisibilityPolicyImpl(MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }),
        handReadinessVisibilityPolicy = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }.let { registry ->
            HandReadinessVisibilityPolicy(moduleRegistry = registry, visibilityPolicy = GameVisibilityPolicyImpl(registry))
        },
        audienceSource = audience,
        sender = sender,
    )

    /** 新觀察者沒有任何送出紀錄，第一次推送就會收到完整內容。 */
    @Test
    fun `a new observer receives the current snapshot`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setRoom(room())
        audience.observers = mapOf(id to setOf(observerId))

        broadcaster.broadcast()

        assertEquals(1, sender.sent.size)
        val (sentId, sentObserverId, snapshot) = sender.sent.single()
        assertEquals(id, sentId)
        assertEquals(observerId, sentObserverId)
        assertIs<ObserverSnapshot.OfRoom>(snapshot)
    }

    /** 觀察者與內容都沒變時不重複送出。 */
    @Test
    fun `an unchanged snapshot is not sent again`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setRoom(room())
        audience.observers = mapOf(id to setOf(observerId))
        broadcaster.broadcast()
        sender.sent.clear()

        broadcaster.broadcast()

        assertTrue(sender.sent.isEmpty())
    }

    /** 內容改變後所有觀察者都收到新內容。 */
    @Test
    fun `a changed room is sent to every observer`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        val aiId = Uuid.random()
        store.setRoom(room(aiStrategyKey = "mahjongcraft:random", aiId = aiId))
        audience.observers = mapOf(id to setOf(hostId, observerId))
        broadcaster.broadcast()
        sender.sent.clear()

        store.setRoom(room(aiStrategyKey = "test:scripted", aiId = aiId))
        broadcaster.broadcast()

        assertEquals(setOf(hostId, observerId), sender.sent.mapTo(mutableSetOf()) { it.second })
        sender.sent.forEach { (_, _, snapshot) ->
            assertEquals("test:scripted", assertIs<ObserverSnapshot.OfRoom>(snapshot).room.aiPlayerStrategyKeys[aiId])
        }
    }

    /** 觀察者離開後紀錄一併刪除，再次成為觀察者時重新送出完整內容。 */
    @Test
    fun `a returning observer is sent the snapshot again`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setRoom(room())
        audience.observers = mapOf(id to setOf(observerId))
        broadcaster.broadcast()
        audience.observers = emptyMap()
        broadcaster.broadcast()
        sender.sent.clear()

        audience.observers = mapOf(id to setOf(observerId))
        broadcaster.broadcast()

        assertEquals(1, sender.sent.size)
        assertIs<ObserverSnapshot.OfRoom>(sender.sent.single().third)
    }

    /** 開局、對局結束回到房間、房間解散時，同一位觀察者依序收到對應內容，解散時收到清除。 */
    @Test
    fun `an observer follows the same id across room and game transitions`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        audience.observers = mapOf(id to setOf(observerId))
        store.setRoom(room())
        broadcaster.broadcast()

        store.setGame(game())
        broadcaster.broadcast()
        store.setRoom(room())
        broadcaster.broadcast()
        store.setRoom(null)
        broadcaster.broadcast()

        val snapshots = sender.sent.map { it.third }
        assertEquals(4, snapshots.size)
        assertIs<ObserverSnapshot.OfRoom>(snapshots[0])
        assertIs<ObserverSnapshot.OfGame>(snapshots[1])
        assertIs<ObserverSnapshot.OfRoom>(snapshots[2])
        assertNull(snapshots[3])
    }

    /** 未入座的觀察者收到的對局內容不含任何手牌。 */
    @Test
    fun `a spectator receives a game snapshot without hand tiles`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setGame(game())
        audience.observers = mapOf(id to setOf(observerId))

        broadcaster.broadcast()

        val snapshot = assertIs<ObserverSnapshot.OfGame>(sender.sent.single().third)
        assertTrue(snapshot.game.players.all { player -> player.hand.standingTiles.all { it.tile == null } })
    }

    /** 對局快照附帶由 AI 操控的玩家。 */
    @Test
    fun `a game snapshot carries the ai players`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        val game = game()
        val aiId = game.tableState.players.last().id
        store.setGame(game.copy(aiPlayerStrategyKeys = mapOf(aiId to "mahjongcraft:random")))
        audience.observers = mapOf(id to setOf(observerId))

        broadcaster.broadcast()

        val snapshot = assertIs<ObserverSnapshot.OfGame>(sender.sent.single().third)
        assertEquals(setOf(aiId), snapshot.aiPlayerIds)
    }

    /** 位置索引先消失時，清理前捕捉的觀察者仍會收到場地失效通知。 */
    @Test
    fun `venue invalidation reaches captured observers after audience disappears`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setRoom(room())
        audience.observers = mapOf(id to setOf(observerId))
        broadcaster.broadcast()
        sender.sent.clear()
        audience.observers = emptyMap()

        broadcaster.requestVenueInvalidation(
            id = id,
            additionalObserverIds = setOf(observerId),
            requestId = Uuid.random(),
            isStillValid = { true },
        )
        broadcaster.broadcast()

        assertEquals(listOf(id to observerId), sender.invalidated)
        assertTrue(sender.sent.isEmpty())
    }

    /** 新來源已取代舊來源時，舊場地失效請求不會清除新來源紀錄。 */
    @Test
    fun `stale invalidation preserves a new source`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setRoom(room())
        audience.observers = mapOf(id to setOf(observerId))
        broadcaster.broadcast()
        sender.sent.clear()

        broadcaster.requestVenueInvalidation(
            id = id,
            additionalObserverIds = setOf(observerId),
            requestId = Uuid.random(),
            isStillValid = { false },
        )
        store.setGame(game())
        broadcaster.broadcast()

        assertTrue(sender.invalidated.isEmpty())
        assertEquals(1, sender.sent.size)
        assertIs<ObserverSnapshot.OfGame>(sender.sent.single().third)
    }

    /** 同一觀察者同時來自既有紀錄、目前 audience 與額外清理名單時只收到一次通知。 */
    @Test
    fun `venue invalidation de duplicates observers`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setRoom(room())
        audience.observers = mapOf(id to setOf(observerId))
        broadcaster.broadcast()
        audience.observers = mapOf(id to setOf(observerId, hostId))

        broadcaster.requestVenueInvalidation(
            id = id,
            additionalObserverIds = setOf(observerId, hostId),
            requestId = Uuid.random(),
            isStillValid = { true },
        )
        broadcaster.broadcast()

        assertEquals(setOf(observerId, hostId), sender.invalidated.mapTo(mutableSetOf()) { it.second })
        assertEquals(2, sender.invalidated.size)
    }

    /** 舊 session 清除後，先前登記的 invalidation 不會在新 session 交付。 */
    @Test
    fun `clear all drops pending invalidations`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        broadcaster.requestVenueInvalidation(id, setOf(observerId), Uuid.random()) { true }

        broadcaster.clearAll()
        broadcaster.broadcast()

        assertTrue(sender.invalidated.isEmpty())
    }

    /** 同一桌的新 invalidation 取代舊請求時，舊請求不會清掉新請求。 */
    @Test
    fun `a newer request replaces an older request safely`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        val oldRequest = Uuid.random()
        val newRequest = Uuid.random()

        broadcaster.requestVenueInvalidation(id, setOf(hostId), oldRequest) { true }
        broadcaster.requestVenueInvalidation(id, setOf(observerId), newRequest) { true }
        broadcaster.broadcast()

        assertEquals(setOf(hostId, observerId), sender.invalidated.mapTo(mutableSetOf()) { it.second })
        assertEquals(2, sender.invalidated.size)
    }

    /** 送出失敗仍會釋放 pending，下一次請求可以再次交付。 */
    @Test
    fun `failed invalidation releases pending`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = object : RecordingSender() {
            var fail = true

            override suspend fun invalidateVenue(
                id: Uuid,
                observerId: Uuid,
                requestId: Uuid,
            ) {
                if (fail) {
                    fail = false
                    throw IllegalStateException("test failure")
                }
                super.invalidateVenue(id, observerId, requestId)
            }
        }
        val broadcaster = broadcaster(store, audience, sender)
        broadcaster.requestVenueInvalidation(id, setOf(observerId), Uuid.random()) { true }

        assertFailsWith<IllegalStateException> { broadcaster.broadcast() }
        broadcaster.requestVenueInvalidation(id, setOf(observerId), Uuid.random()) { true }
        broadcaster.broadcast()

        assertEquals(listOf(id to observerId), sender.invalidated)
    }

    /** 取消交付不會被吞掉，但 pending 仍會釋放。 */
    @Test
    fun `cancelled invalidation releases pending`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = object : RecordingSender() {
            override suspend fun invalidateVenue(
                id: Uuid,
                observerId: Uuid,
                requestId: Uuid,
            ): Unit = throw CancellationException("test cancellation")
        }
        val broadcaster = broadcaster(store, audience, sender)
        broadcaster.requestVenueInvalidation(id, setOf(observerId), Uuid.random()) { true }

        assertFailsWith<CancellationException> { broadcaster.broadcast() }

        assertTrue(sender.invalidated.isEmpty())
    }

    /** 場地失效登記不會等待另一筆尚未完成的快照送出。 */
    @Test
    fun `venue invalidation registration does not wait for an in flight send`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = BlockingSender()
        val broadcaster = broadcaster(store, audience, sender)
        store.setRoom(room())
        audience.observers = mapOf(id to setOf(observerId))

        val broadcastJob = launch { broadcaster.broadcast() }
        sender.sendStarted.await()
        val requestId = Uuid.random()
        broadcaster.requestVenueInvalidation(id, emptySet(), requestId) { true }
        assertTrue(sender.invalidated.isEmpty())

        sender.releaseSend.complete(Unit)
        broadcastJob.join()
        broadcaster.broadcast()

        assertEquals(listOf(id to observerId), sender.invalidated.map { it.first to it.second })
    }

    /** 廣播在查詢觀察者期間若權威狀態更新，送出的內容必須取更新後的狀態。 */
    @Test
    fun `broadcast uses the latest state after audience lookup`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = BlockingAudienceSource()
        val sender = RecordingSender()
        val broadcaster = broadcaster(store, audience, sender)
        audience.observers = mapOf(id to setOf(observerId))

        val broadcastJob = launch { broadcaster.broadcast() }
        audience.lookupStarted.await()
        store.setRoom(room(aiStrategyKey = "test:latest"))
        audience.releaseLookup.complete(Unit)
        broadcastJob.join()

        val snapshot = assertIs<ObserverSnapshot.OfRoom>(sender.sent.single().third)
        assertEquals("test:latest", snapshot.room.aiPlayerStrategyKeys.values.single())
    }

    /** 第一位觀察者送出期間來源被替換時，後續觀察者不得收到舊來源快照。 */
    @Test
    fun `source replacement stops the rest of an old broadcast`() = runTest {
        val store = AuthoritativeStateStore()
        val audience = MutableAudienceSource()
        val sender = FirstSendBlockingSender()
        val broadcaster = broadcaster(store, audience, sender)
        val firstObserverId = Uuid.random()
        val secondObserverId = Uuid.random()
        audience.observers = mapOf(id to setOf(firstObserverId, secondObserverId))
        store.setRoom(room(aiStrategyKey = "test:old"))

        val oldBroadcast = launch { broadcaster.broadcast() }
        sender.firstSendStarted.await()
        store.setRoom(room(aiStrategyKey = "test:new"))
        sender.releaseFirstSend.complete(Unit)
        oldBroadcast.join()

        assertEquals(1, sender.sent.size)
        assertEquals(firstObserverId, sender.sent.single().second)
        assertEquals("test:old", assertIs<ObserverSnapshot.OfRoom>(sender.sent.single().third).room.aiPlayerStrategyKeys.values.single())

        broadcaster.broadcast()

        assertEquals(3, sender.sent.size)
        assertEquals(setOf(firstObserverId, secondObserverId), sender.sent.drop(1).mapTo(mutableSetOf()) { it.second })
        sender.sent.drop(1).forEach { (_, _, snapshot) ->
            assertEquals("test:new", assertIs<ObserverSnapshot.OfRoom>(snapshot).room.aiPlayerStrategyKeys.values.single())
        }
    }
}
