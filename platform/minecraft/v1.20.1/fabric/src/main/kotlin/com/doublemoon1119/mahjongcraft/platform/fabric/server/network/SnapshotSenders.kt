package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 把 `SyncRoomSnapshotUseCase` 已寫入 repository 的快照主動送給指定玩家。 */
@Single
class RoomSnapshotSender(
    private val snapshots: RoomSnapshotRepository,
    private val serverHolder: FabricServerHolder,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 若玩家在線且快照存在，送出一份不伴隨房間事件的同步 payload。 */
    suspend fun send(roomId: Uuid, playerId: Uuid) {
        val player = serverHolder.findPlayer(playerId) ?: return
        val snapshot = snapshots.getSnapshot(roomId, playerId) ?: return
        MahjongChannels.roomSnapshot.sendTo(
            player,
            json,
            RoomSnapshotSyncPayloadDto(roomId.toString(), snapshot.toDto(networkRegistries)),
        )
    }
}

/** 把 `SyncGameSnapshotUseCase` 已寫入 repository 的快照主動送給指定玩家。 */
@Single
class GameSnapshotSender(
    private val snapshots: GameSnapshotRepository,
    private val serverHolder: FabricServerHolder,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 若玩家在線且快照存在，送出一份不伴隨遊戲動作的同步 payload。 */
    suspend fun send(gameId: Uuid, playerId: Uuid) {
        val player = serverHolder.findPlayer(playerId) ?: return
        val snapshot = snapshots.getSnapshot(gameId, playerId) ?: return
        MahjongChannels.gameSnapshot.sendTo(
            player,
            json,
            GameSnapshotSyncPayloadDto(gameId.toString(), snapshot.toDto(networkRegistries)),
        )
    }
}
