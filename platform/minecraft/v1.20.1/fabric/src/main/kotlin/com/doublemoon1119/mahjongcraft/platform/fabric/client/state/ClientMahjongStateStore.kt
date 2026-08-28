package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
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
            managedTileSnapshotsByTileId = buildManagedTileIndex(value)
        }

    /** 目前觀看者可見的開局準備狀態。 */
    var roundPreparationSnapshot: RoundPreparationSnapshot? = null
        private set

    /**
     * 依管理中麻將牌 entity UUID（等同 `IdentifiedTile.id`）索引的快照，隨每次 [gameSnapshot] 更新
     * 一併重建，涵蓋牌牆、所有玩家的手牌（立牌＋剛摸到但尚未整理的牌）與牌河——管理中的 [MahjongTileEntity] 渲染牌面時用這份
     * 索引查詢自己是否對目前觀察者可見，不逐 entity 掃描整份牌牆／手牌／牌河清單。
     */
    private var managedTileSnapshotsByTileId: Map<Uuid, IdentifiedTileSnapshot> = emptyMap()

    /**
     * 匯總牌牆、所有玩家手牌、副露與牌河的牌張快照，建立單一索引。牌河永遠對所有玩家可見
     * （`MahjongPlayerSnapshot.discardPile` 的 KDoc 本來就這樣寫），因此固定以 `isVisible = true`
     * 轉換——`discardPile` 型別是跟 `MahjongPlayer` 共用的 domain `DiscardPile<*>`，不像手牌／牌牆
     * 已經是依觀察者可見範圍轉換過的 snapshot 型別，需要在這裡自行呼叫 `toSnapshot`。副露（`hand.melds`）
     * 本身就恆為完整可見（見 `HandSnapshot.melds` KDoc），直接取用即可，不需要另外轉換可見性。
     */
    private fun buildManagedTileIndex(snapshot: TableStateSnapshot?): Map<Uuid, IdentifiedTileSnapshot> {
        if (snapshot == null) return emptyMap()
        val wallTiles = snapshot.tileWall.tiles
        val handTiles = snapshot.players.flatMap { player ->
            player.hand.standingTiles + listOfNotNull(player.hand.lastDrawn)
        }
        val meldTiles = snapshot.players.flatMap { player ->
            player.hand.melds.flatMap { meld -> meld.tiles }
        }
        val discardTiles = snapshot.players.flatMap { player ->
            player.discardPile.entries.map { entry -> entry.tile.toSnapshot(isVisible = true) }
        }
        return (wallTiles + handTiles + meldTiles + discardTiles).associateBy { it.id }
    }

    /** 接收帶事件的房間更新並保存其最新快照；同一張桌子不會同時是房間又是對局，一併清掉舊的遊戲快照。 */
    fun apply(payload: RoomUpdatePayloadDto) {
        roomSnapshot = payload.snapshot.toDomain(networkRegistries)
        gameSnapshot = null
        roundPreparationSnapshot = null
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
    fun applyGameSnapshot(
        gameId: Uuid,
        snapshot: TableStateSnapshot,
        roundPreparation: RoundPreparationSnapshot? = null,
    ) {
        require(snapshot.id == gameId) { "Game snapshot ID does not match its payload ID." }
        gameSnapshot = snapshot
        roundPreparationSnapshot = roundPreparation
        roomSnapshot = null
    }

    /** 依管理中麻將牌 entity UUID 查詢目前快照中的可見性與牌面；查不到代表這張牌不在目前對局範圍內。 */
    fun findManagedTileSnapshot(tileEntityId: Uuid): IdentifiedTileSnapshot? = managedTileSnapshotsByTileId[tileEntityId]

    /** 清除離開伺服器後不再有效的 client-side 狀態。 */
    fun clear() {
        roomSnapshot = null
        gameSnapshot = null
        roundPreparationSnapshot = null
    }
}
