package com.doublemoon1119.mahjongcraft.flow.server.observer

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證觀察者內容只在改變時送出，且房間與對局互相轉換時不中斷。 */
class ObserverSnapshotBroadcasterTest {
    /** 記錄每次送出的目標與內容。 */
    private class RecordingSender : ObserverSnapshotSender {
        val sent = mutableListOf<Triple<Uuid, Uuid, ObserverSnapshot?>>()

        override suspend fun send(id: Uuid, observerId: Uuid, snapshot: ObserverSnapshot?) {
            sent += Triple(id, observerId, snapshot)
        }
    }

    /** 由測試直接設定的觀察者來源。 */
    private class MutableAudienceSource : ObserverAudienceSource {
        var observers: Map<Uuid, Set<Uuid>> = emptyMap()

        override suspend fun observersById(): Map<Uuid, Set<Uuid>> = observers
    }

    private val id = Uuid.random()
    private val hostId = Uuid.random()
    private val observerId = Uuid.random()

    private fun room(aiStrategyKey: String = "random", aiId: Uuid = Uuid.random()) = Room(
        id = id,
        hostId = hostId,
        gameConfig = GameConfig(RiichiRuleConfig()),
        playerIds = listOf(hostId, aiId),
        readyPlayerIds = listOf(aiId),
        aiPlayerStrategyKeys = mapOf(aiId to aiStrategyKey),
    )

    private fun game() = Game(
        tableState = FakeTableStateFactory.create(
            id = id,
            players = listOf(
                FakeMahjongPlayerFactory.create(seatWind = Wind.EAST, id = hostId),
                FakeMahjongPlayerFactory.create(seatWind = Wind.SOUTH),
            ),
        ),
        flowConfig = GameConfig(RiichiRuleConfig()).flowConfig,
        hostId = hostId,
    )

    private suspend fun AuthoritativeStateStore.setRoom(room: Room?) = update { state ->
        AuthoritativeStateUpdate(
            state = state.copy(
                rooms = if (room == null) state.rooms - id else state.rooms + (id to room),
                games = state.games - id,
            ),
            result = Unit,
        )
    }

    private suspend fun AuthoritativeStateStore.setGame(game: Game) = update { state ->
        AuthoritativeStateUpdate(
            state = state.copy(rooms = state.rooms - id, games = state.games + (id to game)),
            result = Unit,
        )
    }

    private fun broadcaster(
        store: AuthoritativeStateStore,
        audience: MutableAudienceSource,
        sender: RecordingSender,
    ) = ObserverSnapshotBroadcaster(
        store = store,
        visibilityPolicy = GameVisibilityPolicyImpl(),
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
        store.setRoom(room(aiStrategyKey = "random", aiId = aiId))
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
}
