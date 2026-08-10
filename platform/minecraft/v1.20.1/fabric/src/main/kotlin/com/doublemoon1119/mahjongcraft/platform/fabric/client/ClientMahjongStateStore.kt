package com.doublemoon1119.mahjongcraft.platform.fabric.client

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.toDomain
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/** Fabric client 主執行緒持有的最新房間與遊戲 read-side 狀態，供後續 GUI／渲染讀取。 */
object ClientMahjongStateStore {
    /** 目前已同步的房間快照；尚未進入房間時為 null。 */
    var roomSnapshot: RoomSnapshot? = null
        private set

    /** 目前已同步的遊戲快照；尚未開局時為 null。 */
    var gameSnapshot: TableStateSnapshot? = null
        private set

    /** 接收帶事件的房間更新並保存其最新快照。 */
    fun apply(payload: RoomUpdatePayloadDto) {
        roomSnapshot = payload.snapshot.toDomain()
    }

    /** 接收帶動作的遊戲更新並保存其最新快照。 */
    fun apply(payload: GameUpdatePayloadDto) {
        gameSnapshot = payload.snapshot.toDomain()
        roomSnapshot = null
    }

    /** 保存沒有伴隨房間事件的主動同步快照。 */
    fun applyRoomSnapshot(roomId: Uuid, snapshot: RoomSnapshot) {
        require(snapshot.id == roomId) { "Room snapshot ID does not match its payload ID." }
        roomSnapshot = snapshot
    }

    /** 保存沒有伴隨遊戲動作的主動同步快照。 */
    fun applyGameSnapshot(gameId: Uuid, snapshot: TableStateSnapshot) {
        require(snapshot.id == gameId) { "Game snapshot ID does not match its payload ID." }
        gameSnapshot = snapshot
        roomSnapshot = null
    }

    /** 清除離開伺服器後不再有效的 client-side 狀態。 */
    fun clear() {
        roomSnapshot = null
        gameSnapshot = null
    }
}
