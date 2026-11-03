package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.server.room.RoomRosterChangePolicy
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateBusyException
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import kotlin.uuid.Uuid

/**
 * 將已由可信任接入端確認的參與者加入等待房間，不執行外部資格回呼。
 *
 * @property store 同時驗證各房間與對局的唯一來源。
 * @property strategies 已登記的決策策略。
 * @property snapshots 房間觀察者快照。
 * @property events 加入通知。
 * @property rosterPolicy 不讀取外部狀態的完整名冊檢查。
 */
class JoinExternalRoomParticipantUseCase(
    private val store: AuthoritativeStateStore,
    private val strategies: MahjongAiStrategyRegistry,
    private val snapshots: RoomSnapshotRepository,
    private val events: RoomEventPublisher,
    private val rosterPolicy: RoomRosterChangePolicy = RoomRosterChangePolicy.UNRESTRICTED,
) {
    /**
     * 只在房間與預檢相同時提交；來源已被認領或排除時拒絕。
     *
     * @param expected 預檢時讀到的完整房間。
     * @param playerId 分配給本次成員的編號，不代替來源身分。
     * @param identity 接入端驗證的穩定來源。
     * @param strategyKey 已登記的策略。
     * @param playerOrder 加入後的完整順序，只能在原名單中插入新成員。
     * @return 成功加入或拒絕原因；過期資料不寫入。
     */
    suspend operator fun invoke(
        expected: Room,
        playerId: Uuid,
        identity: RoomParticipantIdentity,
        strategyKey: String,
        playerOrder: List<Uuid>,
    ): Outcome<Unit, RoomError> {
        require(identity.sourceId != RoomParticipantIdentity.PLAYER_SOURCE) { "External admission cannot impersonate a player" }
        require(playerId !in expected.playerIds && playerOrder.count { it == playerId } == 1 && playerOrder.filterNot { it == playerId } == expected.playerIds) {
            "External admission must insert exactly one member"
        }
        val result = try {
            store.update(waitForLock = false) { state ->
                val room = state.rooms[expected.id]
                val error = when {
                    room == null -> RoomError.RoomNotFound(expected.id)
                    room != expected -> RoomError.RoomLayoutConflict(expected.id)
                    expected.id in state.games -> RoomError.GameAlreadyInProgress(expected.id)
                    identity in room.admission.excludedParticipants -> RoomError.ParticipantExcluded(expected.id)
                    room.isFull -> RoomError.RoomIsFull(expected.id)
                    strategyKey !in strategies.getAllStrategyKeys() -> RoomError.UnknownAiStrategy(expected.id)
                    state.rooms.values.any { playerId in it.playerIds || identity in it.admission.memberIdentities.values } ||
                        state.games.values.any { playerId in it.roomPlayerIds || identity in it.roomAdmission.memberIdentities.values } ->
                        RoomError.PlayerAlreadyInAnotherGame(playerId, expected.id)
                    else -> null
                }
                if (error != null) return@update AuthoritativeStateUpdate(state, Outcome.Error(error))
                val next = expected.copy(
                    playerIds = playerOrder.toList(),
                    readyPlayerIds = expected.readyPlayerIds + playerId,
                    aiPlayerStrategyKeys = expected.aiPlayerStrategyKeys + (playerId to strategyKey),
                    admission = expected.admission.copy(
                        memberIdentities = expected.admission.memberIdentities + (playerId to identity),
                    ),
                )
                if (!rosterPolicy.allows(expected, next)) {
                    AuthoritativeStateUpdate(state, Outcome.Error(RoomError.RoomLayoutConflict(expected.id)))
                } else {
                    AuthoritativeStateUpdate(state.copy(rooms = state.rooms + (expected.id to next)), Outcome.Success(next))
                }
            }
        } catch (_: AuthoritativeStateBusyException) {
            return Outcome.Error(RoomError.RoomLayoutConflict(expected.id))
        }
        return when (result) {
            is Outcome.Error -> result
            is Outcome.Success -> {
                val room = result.value
                snapshots.getAllObservers(room.id).forEach { snapshots.setSnapshot(it, room.toSnapshot(it)) }
                room.playerIds.forEach { events.publishJoin(room.id, it, playerId, JoinReason.Joined) }
                Outcome.Success(Unit)
            }
        }
    }
}
