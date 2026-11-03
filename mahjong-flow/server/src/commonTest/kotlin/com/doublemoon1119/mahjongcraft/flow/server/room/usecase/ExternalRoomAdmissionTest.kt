package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 外部實體的來源唯一性、過期預檢與排除不得依臨時成員編號繞過。 */
class ExternalRoomAdmissionTest {
    /** 不建立策略實例，只驗證登記狀態。 */
    private val strategies = MahjongAiStrategyRegistryImpl("example:strategy").apply {
        register(MahjongAiStrategyDefinition("example:strategy") { error("Strategy creation is not part of admission") })
        freeze()
    }

    /** 忙碌時立即拒絕外部入房，交易結束後不會遲到地提交舊預檢。 */
    @Test
    fun `external admission refuses a busy store without waiting or publishing`() = runTest {
        val store = AuthoritativeStateStore()
        val rooms = RoomRepositoryImpl(store)
        val room = room()
        rooms.setRoom(room)
        val release = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) {
            store.update { state ->
                release.await()
                AuthoritativeStateUpdate(state, Unit)
            }
        }
        try {
            val join = JoinExternalRoomParticipantUseCase(store, strategies, FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
            val actor = Uuid.random()
            val joining = async(start = CoroutineStart.UNDISPATCHED) {
                join(room, actor, RoomParticipantIdentity("example:actors", Uuid.random()), "example:strategy", room.playerIds + actor)
            }
            assertTrue(joining.isCompleted, "External admission must not wait for the authoritative lock")
            assertIs<Outcome.Error<RoomError>>(joining.await())
            assertEquals(room, store.state.value.rooms[room.id])
        } finally {
            release.complete(Unit)
            holder.join()
        }
        assertEquals(room, rooms.getRoom(room.id))
    }

    /** 同一來源跨房間與換成員編號仍只能加入一次。 */
    @Test
    fun `same external identity cannot join another room with a new player id`() = runTest {
        val store = AuthoritativeStateStore()
        val rooms = RoomRepositoryImpl(store)
        val join = JoinExternalRoomParticipantUseCase(store, strategies, FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        val first = room()
        val second = room()
        rooms.setRoom(first)
        rooms.setRoom(second)
        val identity = RoomParticipantIdentity("example:actors", Uuid.random())
        val player = Uuid.random()
        assertIs<Outcome.Success<Unit>>(join(first, player, identity, "example:strategy", first.playerIds + player))
        val another = Uuid.random()
        assertIs<Outcome.Error<RoomError>>(join(second, another, identity, "example:strategy", second.playerIds + another))
        assertEquals(second, rooms.getRoom(second.id))
        assertEquals(identity, rooms.getRoom(first.id)!!.admission.memberIdentities[player])
    }

    /** 解散重建或被踢後更換成員編號都不會越過權威檢查。 */
    @Test
    fun `stale room and excluded source refuse admission without partial member`() = runTest {
        val store = AuthoritativeStateStore()
        val rooms = RoomRepositoryImpl(store)
        val join = JoinExternalRoomParticipantUseCase(store, strategies, FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        val before = room()
        rooms.setRoom(before.copy(admission = RoomAdmissionState()))
        val identity = RoomParticipantIdentity("example:actors", Uuid.random())
        val player = Uuid.random()
        assertIs<Outcome.Error<RoomError>>(join(before, player, identity, "example:strategy", before.playerIds + player))
        val excluded = before.copy(admission = before.admission.copy(excludedParticipants = mapOf(identity to Uuid.random())))
        rooms.setRoom(excluded)
        assertEquals(RoomError.ParticipantExcluded(before.id), assertIs<Outcome.Error<RoomError>>(join(excluded, player, identity, "example:strategy", before.playerIds + player)).error)
        assertEquals(excluded, rooms.getRoom(before.id))
        assertTrue(rooms.getRoom(before.id)!!.aiPlayerIds.isEmpty())
    }

    /**
     * 建立只有房主的等待房間。
     *
     * @return 可新增外部成員的房間。
     */
    private fun room(): Room {
        val host = Uuid.random()
        return Room(Uuid.random(), host, GameConfig(FakeMahjongRuleConfig()), listOf(host))
    }
}
