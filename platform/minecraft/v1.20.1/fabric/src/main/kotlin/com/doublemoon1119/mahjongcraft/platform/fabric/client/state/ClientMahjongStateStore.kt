package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.TableLobbyPhaseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 單一桌子的 read-side 快照集合；[ClientMahjongStateStore] 依桌子 UUID 各自保存一份，互不覆蓋。
 */
private data class ClientTableState(
    val tableLobby: TableLobbyPayloadDto? = null,
    val roomSnapshot: RoomSnapshot? = null,
    val gameSnapshot: TableStateSnapshot? = null,
    val roundPreparationSnapshot: RoundPreparationSnapshot? = null,
    val managedTileSnapshotsByTileId: Map<Uuid, IdentifiedTileSnapshot> = emptyMap(),
) {
    /** 更新 [gameSnapshot] 時一併重建 [managedTileSnapshotsByTileId]，兩者不會不同步。 */
    fun withGameSnapshot(snapshot: TableStateSnapshot?): ClientTableState = copy(
        gameSnapshot = snapshot,
        managedTileSnapshotsByTileId = buildManagedTileIndex(snapshot),
    )

    private companion object {
        /**
         * 匯總牌牆、所有玩家手牌、副露與牌河的牌張快照，建立單一索引。牌河永遠對所有玩家可見
         * （`MahjongPlayerSnapshot.discardPile` 的 KDoc 本來就這樣寫），因此固定以 `isVisible = true`
         * 轉換——`discardPile` 型別是跟 `MahjongPlayer` 共用的 domain `DiscardPile<*>`，不像手牌／牌牆
         * 已經是依觀察者可見範圍轉換過的 snapshot 型別，需要在這裡自行呼叫 `toSnapshot`。副露
         * （`hand.melds`）本身就恆為完整可見（見 `HandSnapshot.melds` KDoc），直接取用即可，不需要另外
         * 轉換可見性。
         */
        fun buildManagedTileIndex(snapshot: TableStateSnapshot?): Map<Uuid, IdentifiedTileSnapshot> {
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
    }
}

/**
 * Fabric client 主執行緒持有的最新房間與遊戲 read-side 狀態，供後續 GUI／渲染讀取。
 *
 * 依桌子 UUID 索引，不是單一全域欄位：一個 client 同時可能對好幾張桌子都有資料——除了自己所在的房間，
 * 任何因為 entity tracking 觸發被動旁觀的其他桌子也會推播資料進來。用單一全域欄位存放的話，不相干
 * 桌子的推播會互相覆蓋（例如旁邊桌子隨便一個動作，就把自己正在看的房間資料洗掉、連帶把畫面關掉）。
 */
@Single
class ClientMahjongStateStore(
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    private val tables = mutableMapOf<Uuid, ClientTableState>()

    fun tableLobby(tableId: Uuid): TableLobbyPayloadDto? = tables[tableId]?.tableLobby

    fun roomSnapshot(tableId: Uuid): RoomSnapshot? = tables[tableId]?.roomSnapshot

    fun gameSnapshot(tableId: Uuid): TableStateSnapshot? = tables[tableId]?.gameSnapshot

    fun roundPreparationSnapshot(tableId: Uuid): RoundPreparationSnapshot? = tables[tableId]?.roundPreparationSnapshot

    /** 依管理中麻將牌 entity UUID 查詢指定桌子目前快照中的可見性與牌面；查不到代表這張牌不在目前對局範圍內。 */
    fun findManagedTileSnapshot(tableId: Uuid, tileEntityId: Uuid): IdentifiedTileSnapshot? = tables[tableId]?.managedTileSnapshotsByTileId?.get(tileEntityId)

    /**
     * 找出本地玩家目前真正身處（不是被動旁觀）的桌子 ID；等待室階段看 [RoomSnapshot.isInRoom]，
     * 對局中看玩家是否還在座位名單裡，兩者最多同時只有一個成立。找不到代表玩家目前不在任何房間內。
     */
    fun findTableWhereSeated(localPlayerId: Uuid): Uuid? = tables.entries.firstOrNull { (_, state) ->
        state.roomSnapshot?.isInRoom == true || state.gameSnapshot?.players?.any { it.id == localPlayerId } == true
    }?.key

    /** 接收帶事件的房間更新並保存其最新快照；同一張桌子不會同時是房間又是對局，一併清掉舊的遊戲快照。 */
    fun apply(payload: RoomUpdatePayloadDto) {
        val tableId = Uuid.parse(payload.roomId)
        updateTable(tableId) { current ->
            current.withGameSnapshot(null).copy(
                roomSnapshot = payload.snapshot.toDomain(networkRegistries),
                tableLobby = current.tableLobby?.copy(phase = TableLobbyPhaseDto.WAITING),
                roundPreparationSnapshot = null,
            )
        }
    }

    /** 保存 RoomScreen 的桌級狀態；等待房間 payload 同時更新其內嵌快照。 */
    fun apply(payload: TableLobbyPayloadDto) {
        val tableId = Uuid.parse(payload.tableId)
        updateTable(tableId) { current ->
            current.copy(
                tableLobby = payload,
                roomSnapshot = payload.roomSnapshot?.toDomain(networkRegistries) ?: current.roomSnapshot,
            )
        }
    }

    /** 接收帶動作的遊戲更新並保存其最新快照。 */
    fun apply(payload: GameUpdatePayloadDto) {
        val tableId = Uuid.parse(payload.gameId)
        updateTable(tableId) { current ->
            val waitingConfig = current.roomSnapshot?.gameConfig?.toDto(networkRegistries)
            current.withGameSnapshot(payload.snapshot.toDomain(networkRegistries)).copy(
                tableLobby = current.tableLobby?.copy(
                    phase = TableLobbyPhaseDto.PLAYING,
                    playingGameConfig = current.tableLobby.playingGameConfig ?: waitingConfig,
                ),
                roomSnapshot = null,
            )
        }
    }

    /** 保存沒有伴隨房間事件的主動同步快照；同一張桌子不會同時是房間又是對局，一併清掉舊的遊戲快照。 */
    fun applyRoomSnapshot(roomId: Uuid, snapshot: RoomSnapshot) {
        require(snapshot.id == roomId) { "Room snapshot ID does not match its payload ID." }
        updateTable(roomId) { current ->
            current.withGameSnapshot(null).copy(
                roomSnapshot = snapshot,
                tableLobby = current.tableLobby?.copy(phase = TableLobbyPhaseDto.WAITING),
            )
        }
    }

    /** 保存沒有伴隨遊戲動作的主動同步快照。 */
    fun applyGameSnapshot(
        gameId: Uuid,
        snapshot: TableStateSnapshot,
        roundPreparation: RoundPreparationSnapshot? = null,
    ) {
        require(snapshot.id == gameId) { "Game snapshot ID does not match its payload ID." }
        updateTable(gameId) { current ->
            val waitingConfig = current.roomSnapshot?.gameConfig?.toDto(networkRegistries)
            current.withGameSnapshot(snapshot).copy(
                tableLobby = current.tableLobby?.copy(
                    phase = TableLobbyPhaseDto.PLAYING,
                    playingGameConfig = current.tableLobby.playingGameConfig ?: waitingConfig,
                ),
                roundPreparationSnapshot = roundPreparation,
                roomSnapshot = null,
            )
        }
    }

    private fun updateTable(tableId: Uuid, transform: (ClientTableState) -> ClientTableState) {
        tables[tableId] = transform(tables[tableId] ?: ClientTableState())
    }

    /** 清除離開伺服器後不再有效的 client-side 狀態。 */
    fun clear() {
        tables.clear()
    }
}
