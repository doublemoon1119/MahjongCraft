package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import kotlin.uuid.Uuid

/**
 * 房主解除暫時排除；不直接加入成員。
 *
 * @property rooms 權威房間。
 * @property snapshots 依觀察者裁切的房間快照。
 */
class AllowRoomParticipantUseCase(
    private val rooms: RoomRepository,
    private val snapshots: RoomSnapshotRepository,
) {
    /**
     * 解除指定成員上次留下的排除記錄。
     *
     * @param roomId 目前房間。
     * @param operatorId 發起操作的房主。
     * @param previousPlayerId 被排除者上次的成員編號；null 表示解除整份名單。
     * @return 是否成功；重複解除不會加入或移動任何成員。
     */
    suspend operator fun invoke(
        roomId: Uuid,
        operatorId: Uuid,
        previousPlayerId: Uuid?,
    ): Outcome<Unit, RoomError> {
        val result = rooms.update(roomId) { room ->
            when {
                room == null -> room to Outcome.Error(RoomError.RoomNotFound(roomId))
                room.hostId != operatorId -> room to Outcome.Error(RoomError.NotHost(operatorId))
                else -> {
                    val next = room.copy(
                        admission = room.admission.copy(
                            excludedParticipants = if (previousPlayerId == null) emptyMap() else room.admission.excludedParticipants.filterValues { it != previousPlayerId },
                        ),
                    )
                    next to Outcome.Success(next)
                }
            }
        }
        return when (result) {
            is Outcome.Error -> result
            is Outcome.Success -> {
                snapshots.getAllObservers(roomId).forEach { snapshots.setSnapshot(it, result.value.toSnapshot(it)) }
                Outcome.Success(Unit)
            }
        }
    }
}
