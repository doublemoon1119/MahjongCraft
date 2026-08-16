package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** Fabric client 主執行緒持有的最新房間與遊戲 read-side 狀態，供後續 GUI／渲染讀取。 */
@Single
class ClientMahjongStateStore(
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 目前已同步的房間快照；尚未進入房間時為 null。 */
    var roomSnapshot: RoomSnapshot? = null
        private set

    /** 目前已同步的遊戲快照；尚未開局時為 null。 */
    var gameSnapshot: TableStateSnapshot? = null
        private set(value) {
            field = value
            wallTileSnapshotsByTileId = value?.tileWall?.tiles?.associateBy { it.id } ?: emptyMap()
        }

    /**
     * 依牌牆用牌 entity UUID（等同 `IdentifiedTile.id`）索引的快照，隨每次 [gameSnapshot] 更新一併
     * 重建；管理中的 [com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity] 渲染
     * 牌面時用這份索引查詢自己是否對目前觀察者可見，不逐 entity 掃描整份牌牆清單。
     */
    private var wallTileSnapshotsByTileId: Map<Uuid, IdentifiedTileSnapshot> = emptyMap()

    /** 接收帶事件的房間更新並保存其最新快照；同一張桌子不會同時是房間又是對局，一併清掉舊的遊戲快照。 */
    fun apply(payload: RoomUpdatePayloadDto) {
        roomSnapshot = payload.snapshot.toDomain(networkRegistries)
        gameSnapshot = null
    }

    /** 接收帶動作的遊戲更新並保存其最新快照。 */
    fun apply(payload: GameUpdatePayloadDto) {
        gameSnapshot = payload.snapshot.toDomain(networkRegistries)
        roomSnapshot = null
    }

    /** 保存沒有伴隨房間事件的主動同步快照；同一張桌子不會同時是房間又是對局，一併清掉舊的遊戲快照。 */
    fun applyRoomSnapshot(roomId: Uuid, snapshot: RoomSnapshot) {
        require(snapshot.id == roomId) { "Room snapshot ID does not match its payload ID." }
        roomSnapshot = snapshot
        gameSnapshot = null
    }

    /** 保存沒有伴隨遊戲動作的主動同步快照。 */
    fun applyGameSnapshot(gameId: Uuid, snapshot: TableStateSnapshot) {
        require(snapshot.id == gameId) { "Game snapshot ID does not match its payload ID." }
        gameSnapshot = snapshot
        roomSnapshot = null
    }

    /** 依牌牆用牌 entity UUID 查詢目前快照中的可見性與牌面；查不到代表這張牌不在目前對局範圍內。 */
    fun findWallTileSnapshot(tileEntityId: Uuid): IdentifiedTileSnapshot? = wallTileSnapshotsByTileId[tileEntityId]

    /** 清除離開伺服器後不再有效的 client-side 狀態。 */
    fun clear() {
        roomSnapshot = null
        gameSnapshot = null
    }
}
