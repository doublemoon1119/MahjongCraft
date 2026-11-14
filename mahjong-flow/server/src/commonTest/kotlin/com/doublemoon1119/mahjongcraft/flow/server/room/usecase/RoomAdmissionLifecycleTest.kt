package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 使用真實權威儲存驗證排除與重新加入。 */
class RoomAdmissionLifecycleTest {
    /** 全部解除與個別解除同樣驗證房主，不重新加入任何成員。 */
    @Test
    fun `allow all requires host and preserves the roster`() = runTest {
        val host = Uuid.random()
        val ids = listOf(Uuid.random(), Uuid.random())
        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = listOf(host),
            admission = RoomAdmissionState(excludedParticipants = ids.associate { RoomParticipantIdentity.player(it) to it }),
        )
        val rooms = RoomRepositoryImpl(AuthoritativeStateStore())
        rooms.setRoom(room)
        val allow = AllowRoomParticipantUseCase(rooms, FakeRoomSnapshotRepository())
        assertIs<Outcome.Error<RoomError>>(allow(room.id, ids.first(), null))
        assertEquals(room, rooms.getRoom(room.id))
        assertIs<Outcome.Success<Unit>>(allow(room.id, host, null))
        val result = rooms.getRoom(room.id)!!
        assertTrue(result.admission.excludedParticipants.isEmpty())
        assertEquals(room.playerIds, result.playerIds)
        assertEquals(room.admission.instanceId, result.admission.instanceId)
    }

    /** 排除名單已滿時保留原成員與排除身分，不丟棄最舊記錄。 */
    @Test
    fun `full exclusion list refuses a new kick without losing existing entries`() = runTest {
        val host = Uuid.random()
        val guest = Uuid.random()
        val excluded = List(RoomAdmissionState.MAX_EXCLUDED_PARTICIPANTS) { Uuid.random() }.associate { RoomParticipantIdentity.player(it) to it }
        val room = Room(Uuid.random(), host, GameConfig(FakeMahjongRuleConfig()), listOf(host, guest), admission = RoomAdmissionState(excludedParticipants = excluded))
        val rooms = RoomRepositoryImpl(AuthoritativeStateStore())
        rooms.setRoom(room)
        val kick = KickPlayerUseCase(rooms, PlayerMembershipRepositoryImpl(), FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        assertEquals(RoomError.ExclusionListFull(room.id), assertIs<Outcome.Error<RoomError>>(kick(room.id, host, guest)).error)
        assertEquals(room, rooms.getRoom(room.id))
    }

    /** 自動離座依據過期時不移除新房間的成員，也不發離開通知。 */
    @Test
    fun `stale automatic departure leaves current room unchanged`() = runTest {
        val rooms = RoomRepositoryImpl(AuthoritativeStateStore())
        val host = Uuid.random()
        val guest = Uuid.random()
        val expected = Room(Uuid.random(), host, GameConfig(FakeMahjongRuleConfig()), listOf(host, guest))
        val current = expected.copy(admission = RoomAdmissionState())
        rooms.setRoom(current)
        val leave = LeaveRoomUseCase(rooms, PlayerMembershipRepositoryImpl(), FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        assertIs<Outcome.Error<RoomError>>(leave(current.id, guest, expectedRoom = expected))
        assertEquals(current, rooms.getRoom(current.id))
        assertIs<Outcome.Success<Unit>>(leave(current.id, guest, expectedRoom = current))
        assertEquals(listOf(host), rooms.getRoom(current.id)!!.playerIds)
    }

    /** 生成式 AI 沒有可重新加入的來源，不把其隨機編號當成被排除真人。 */
    @Test
    fun `removing generated AI does not create a rejoin exclusion`() = runTest {
        val rooms = RoomRepositoryImpl(AuthoritativeStateStore())
        val host = Uuid.random()
        val ai = Uuid.random()
        val room = Room(Uuid.random(), host, GameConfig(FakeMahjongRuleConfig()), listOf(host, ai), aiPlayerStrategyKeys = mapOf(ai to "test:strategy"))
        rooms.setRoom(room)
        val kick = KickPlayerUseCase(rooms, PlayerMembershipRepositoryImpl(), FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        assertIs<Outcome.Success<Unit>>(kick(room.id, host, ai))
        assertTrue(rooms.getRoom(room.id)!!.admission.excludedParticipants.isEmpty())
    }

    /** 真人被踢後不能重入，非房主不能解除；允許後仍須自行加入。 */
    @Test
    fun `kicked player remains excluded until host allows rejoining`() = runTest {
        val rooms = RoomRepositoryImpl(AuthoritativeStateStore())
        val memberships = PlayerMembershipRepositoryImpl()
        val snapshots = FakeRoomSnapshotRepository()
        val events = FakeRoomEventPublisher()
        val host = Uuid.random()
        val guest = Uuid.random()
        val room = Room(Uuid.random(), host, GameConfig(FakeMahjongRuleConfig()), listOf(host, guest))
        rooms.setRoom(room)
        val kick = KickPlayerUseCase(rooms, memberships, snapshots, events)
        val join = JoinRoomUseCase(rooms, memberships, snapshots, events)
        val allow = AllowRoomParticipantUseCase(rooms, snapshots)
        assertIs<Outcome.Success<Unit>>(kick(room.id, host, guest))
        assertEquals(RoomError.ParticipantExcluded(room.id), assertIs<Outcome.Error<RoomError>>(join(room.id, guest)).error)
        assertIs<Outcome.Error<RoomError>>(allow(room.id, guest, guest))
        assertEquals(room.admission.instanceId, rooms.getRoom(room.id)!!.admission.instanceId)
        assertIs<Outcome.Success<Unit>>(allow(room.id, host, guest))
        assertFalse(guest in rooms.getRoom(room.id)!!.playerIds)
        assertIs<Outcome.Success<Unit>>(join(room.id, guest))
    }

    /** 排除鍵依來源身分，不依可變策略或重新分配的成員編號。 */
    @Test
    fun `external identity survives member removal and is private to host`() = runTest {
        val host = Uuid.random()
        val actor = Uuid.random()
        val identity = RoomParticipantIdentity("example:actors", Uuid.random())
        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = listOf(host, actor),
            aiPlayerStrategyKeys = mapOf(actor to "example:strategy"),
            admission = RoomAdmissionState(memberIdentities = mapOf(actor to identity)),
        )
        val rooms = RoomRepositoryImpl(AuthoritativeStateStore())
        rooms.setRoom(room)
        val kick = KickPlayerUseCase(rooms, PlayerMembershipRepositoryImpl(), FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        assertIs<Outcome.Success<Unit>>(kick(room.id, host, actor))
        val next = rooms.getRoom(room.id)!!
        assertEquals(mapOf(identity to actor), next.admission.excludedParticipants)
        assertTrue(next.admission.memberIdentities.isEmpty())
        assertEquals(listOf(actor), next.toSnapshot(host).excludedPlayerIds)
        assertTrue(next.toSnapshot(Uuid.random()).excludedPlayerIds.isEmpty())
        assertEquals(listOf(actor), next.toSnapshot(host).excludedAiPlayerIds)
        assertTrue(next.toSnapshot(Uuid.random()).excludedAiPlayerIds.isEmpty())
        assertTrue(RoomAdmissionState().excludedParticipants.isEmpty())
        assertNotEquals(room.admission.instanceId, RoomAdmissionState().instanceId)
    }

    /** 主動離開只刪除本人成員記錄，保留其他角色來源與房間識別。 */
    @Test
    fun `voluntary leave retains other actors identities and the room instance`() = runTest {
        val store = AuthoritativeStateStore()
        val rooms = RoomRepositoryImpl(store)
        val host = Uuid.random()
        val actor = Uuid.random()
        val guest = Uuid.random()
        val original = listOf(host, actor, guest)
        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = original,
            aiPlayerStrategyKeys = mapOf(actor to "example:strategy"),
            admission = RoomAdmissionState(
                memberIdentities = mapOf(actor to RoomParticipantIdentity("example:actors", Uuid.random())),
            ),
        )
        rooms.setRoom(room)
        val leave = LeaveRoomUseCase(rooms, PlayerMembershipRepositoryImpl(), FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        assertIs<Outcome.Success<Unit>>(leave(room.id, guest, expectedRoom = room))
        assertEquals(listOf(host, actor), rooms.getRoom(room.id)!!.playerIds)
        assertEquals(room.admission.memberIdentities, rooms.getRoom(room.id)!!.admission.memberIdentities)
        assertEquals(room.admission.instanceId, rooms.getRoom(room.id)!!.admission.instanceId)
    }
}
