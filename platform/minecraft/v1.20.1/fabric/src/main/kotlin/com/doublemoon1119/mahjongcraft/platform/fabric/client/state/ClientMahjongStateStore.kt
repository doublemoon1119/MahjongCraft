package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdateEventDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.LeaveReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyPayloadDto
import kotlin.uuid.Uuid

/**
 * 單一桌子的 read-side 快照集合；[ClientMahjongStateStore] 依桌子 UUID 各自保存一份，互不覆蓋。
 *
 * @property tableOccupancy 該桌目前的大廳占用狀態。
 * @property roomSnapshot 等待室快照。
 * @property gameSnapshot 對局快照。
 * @property gameAiPlayerIds 由 AI 操控的玩家 UUID。
 * @property roundPreparationSnapshot 開局準備快照。
 * @property handReadinessAnalysis 只提供給該桌參與者的手牌分析。
 * @property managedTileSnapshotsByTileId 依牌 entity UUID 索引的管理中牌快照。
 */
private data class ClientTableState(
    val tableOccupancy: TableOccupancyPayloadDto? = null,
    val roomSnapshot: RoomSnapshot? = null,
    val gameSnapshot: TableStateSnapshot? = null,
    val gameAiPlayerIds: Set<Uuid> = emptySet(),
    val roundPreparationSnapshot: RoundPreparationSnapshot? = null,
    val handReadinessAnalysis: HandReadinessAnalysisDto? = null,
    val managedTileSnapshotsByTileId: Map<Uuid, IdentifiedTileSnapshot> = emptyMap(),
) {
    /**
     * 更新 [gameSnapshot] 時一併更新 [gameAiPlayerIds] 並重建 [managedTileSnapshotsByTileId]，三者不會不同步。
     *
     * @param snapshot 新的對局快照；`null` 代表清除對局資料。
     * @param aiPlayerIds 由 AI 操控的玩家 UUID；清除快照時會一併清空。
     * @return 更新後的桌子狀態。
     */
    fun withGameSnapshot(snapshot: TableStateSnapshot?, aiPlayerIds: Set<Uuid> = emptySet()): ClientTableState = copy(
        gameSnapshot = snapshot,
        gameAiPlayerIds = if (snapshot == null) emptySet() else aiPlayerIds,
        handReadinessAnalysis = if (snapshot == null) null else handReadinessAnalysis,
        managedTileSnapshotsByTileId = buildManagedTileIndex(snapshot),
    )

    private companion object {
        /**
         * 彙整牌牆、所有玩家手牌、副露、牌河、移出手牌與手牌中已公開的牌張快照，依 entity UUID 建立索引。牌河與移出手牌的牌永遠對
         * 所有玩家可見（`MahjongPlayerSnapshot.discardPile`／`setAsideTiles` 的 KDoc），因此固定以
         * `isVisible = true` 轉換——兩者都是 domain 型別，不像手牌／牌牆
         * 已經是依觀察者可見範圍轉換過的 snapshot 型別，需要在這裡自行呼叫 `toSnapshot`。副露
         * （`hand.melds`）本身就恆為完整可見（見 `HandSnapshot.melds` KDoc），直接取用即可，不需要另外
         * 轉換可見性。
         *
         * @param snapshot 對局快照；`null` 時回傳空索引。
         * @return 依牌 entity UUID 索引的管理中牌快照。
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
            val setAsideTiles = snapshot.players.flatMap { player ->
                player.setAsideTiles.map { tile -> tile.toSnapshot(isVisible = true) }
            }
            // 已公開但仍在手牌中的牌（等待搶和的宣告牌、被搶和胡走的牌）對其他觀察者是隱藏的手牌；排在最後覆蓋成公開牌面。
            val revealedHandTiles = snapshot.revealedHandTiles.map { tile -> tile.toSnapshot(isVisible = true) }
            return (wallTiles + handTiles + meldTiles + discardTiles + setAsideTiles + revealedHandTiles).associateBy { it.id }
        }
    }
}

/**
 * Fabric client 主執行緒持有的最新房間與遊戲 read-side 狀態，供後續 GUI／渲染讀取。
 *
 * 依桌子 UUID 索引，不是單一全域欄位：一個 client 同時可能對好幾張桌子都有資料——除了自己所在的房間，
 * 任何因為 entity tracking 觸發被動旁觀的其他桌子也會推播資料進來。用單一全域欄位存放的話，不相干
 * 桌子的推播會互相覆蓋（例如旁邊桌子隨便一個動作，就把自己正在看的房間資料洗掉、連帶把畫面關掉）。
 *
 * @param networkRegistries 網路 DTO 與領域快照轉換所需的 registry。
 */
class ClientMahjongStateStore(
    private val networkRegistries: NetworkDtoRegistries,
) {
    private val tables = mutableMapOf<Uuid, ClientTableState>()

    /**
     * 查詢指定桌子的大廳占用狀態。
     *
     * @param tableId 桌子 UUID。
     */
    fun tableOccupancy(tableId: Uuid): TableOccupancyPayloadDto? = tables[tableId]?.tableOccupancy

    /**
     * 查詢指定桌子的等待室快照。
     *
     * @param tableId 桌子 UUID。
     */
    fun roomSnapshot(tableId: Uuid): RoomSnapshot? = tables[tableId]?.roomSnapshot

    /**
     * 查詢指定桌子的對局快照。
     *
     * @param tableId 桌子 UUID。
     */
    fun gameSnapshot(tableId: Uuid): TableStateSnapshot? = tables[tableId]?.gameSnapshot

    /**
     * 取得指定桌子目前對局中由 AI 操控的玩家；沒有對局快照時為空集合。
     *
     * @param tableId 桌子 UUID。
     */
    fun gameAiPlayerIds(tableId: Uuid): Set<Uuid> = tables[tableId]?.gameAiPlayerIds.orEmpty()

    /**
     * 查詢指定桌子的開局準備快照。
     *
     * @param tableId 桌子 UUID。
     */
    fun roundPreparationSnapshot(tableId: Uuid): RoundPreparationSnapshot? = tables[tableId]?.roundPreparationSnapshot

    /**
     * 取得只向該桌參與者本人同步的目前手牌分析。
     *
     * @param tableId 桌子 UUID。
     */
    fun handReadinessAnalysis(tableId: Uuid): HandReadinessAnalysisDto? = tables[tableId]?.handReadinessAnalysis

    /**
     * 依管理中麻將牌 entity UUID 查詢指定桌子目前快照中的可見性與牌面；查不到代表這張牌不在目前對局範圍內。
     *
     * @param tableId 桌子 UUID。
     * @param tileEntityId 麻將牌 entity UUID。
     */
    fun findManagedTileSnapshot(tableId: Uuid, tileEntityId: Uuid): IdentifiedTileSnapshot? = tables[tableId]?.managedTileSnapshotsByTileId?.get(tileEntityId)

    /**
     * 找出本地玩家目前真正身處（不是被動旁觀）的桌子 ID；等待室階段看 [RoomSnapshot.isInRoom]，
     * 對局中看玩家是否還在座位名單裡，兩者最多同時只有一個成立。找不到代表玩家目前不在任何房間內。
     *
     * @param localPlayerId 本地玩家 UUID。
     */
    fun findTableWhereSeated(localPlayerId: Uuid): Uuid? = tables.entries.firstOrNull { (_, state) ->
        state.roomSnapshot?.isInRoom == true || state.gameSnapshot?.players?.any { it.id == localPlayerId } == true
    }?.key

    /**
     * 接收帶事件的房間更新並保存其最新快照；同一張桌子不會同時是房間又是對局，一併清掉舊的遊戲快照。
     *
     * 解散是唯一看事件種類的情況：房間已經不存在，payload 附帶的是解散前的快照，保存它會讓畫面退回
     * 解散前的內容，因此改為清除（[applySnapshotCleared]）。
     *
     * @param payload 房間更新事件與其快照。
     */
    fun apply(payload: RoomUpdatePayloadDto) {
        val tableId = Uuid.parse(payload.roomId)
        val event = payload.event
        if (event is RoomUpdateEventDto.Leave && event.reason == LeaveReasonDto.Dissolved) {
            applySnapshotCleared(tableId)
            return
        }
        updateTable(tableId) { current ->
            current.withGameSnapshot(null).copy(
                roomSnapshot = payload.snapshot.toDomain(networkRegistries),
                tableOccupancy = current.tableOccupancy?.copy(occupancy = TableOccupancyDto.ROOM),
                roundPreparationSnapshot = null,
                handReadinessAnalysis = null,
            )
        }
    }

    /**
     * 保存 RoomScreen 的桌級狀態；等待房間 payload 同時更新其內嵌快照。
     *
     * @param payload 桌級占用狀態與可選的房間快照。
     */
    fun apply(payload: TableOccupancyPayloadDto) {
        val tableId = Uuid.parse(payload.tableId)
        updateTable(tableId) { current ->
            current.copy(
                tableOccupancy = payload,
                roomSnapshot = payload.roomSnapshot?.toDomain(networkRegistries) ?: current.roomSnapshot,
            )
        }
    }

    /**
     * 接收帶動作的遊戲更新並保存其最新快照。手牌分析只隨主動同步快照（[applyGameSnapshot]）更新：兩種封包的抵達順序不固定，
     * 動作更新不清除也不改變目前的手牌分析。
     *
     * @param payload 遊戲狀態更新及 AI 操控玩家清單。
     */
    fun apply(payload: GameUpdatePayloadDto) {
        val tableId = Uuid.parse(payload.gameId)
        updateTable(tableId) { current ->
            val waitingConfig = current.roomSnapshot?.gameConfig?.toDto(networkRegistries)
            current.withGameSnapshot(
                payload.snapshot.toDomain(networkRegistries),
                payload.aiPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
            ).copy(
                tableOccupancy = current.tableOccupancy?.copy(
                    occupancy = TableOccupancyDto.GAME,
                    playingGameConfig = current.tableOccupancy.playingGameConfig ?: waitingConfig,
                ),
                roomSnapshot = null,
            )
        }
    }

    /**
     * 保存沒有伴隨房間事件的主動同步快照；同一張桌子不會同時是房間又是對局，一併清掉舊的遊戲快照。
     *
     * @param roomId 房間 UUID，須與 [snapshot] 識別碼相同。
     * @param snapshot 最新房間快照。
     */
    fun applyRoomSnapshot(roomId: Uuid, snapshot: RoomSnapshot) {
        require(snapshot.id == roomId) { "Room snapshot ID does not match its payload ID." }
        updateTable(roomId) { current ->
            current.withGameSnapshot(null).copy(
                roomSnapshot = snapshot,
                tableOccupancy = current.tableOccupancy?.copy(occupancy = TableOccupancyDto.ROOM),
            )
        }
    }

    /**
     * 清除一張桌子的房間與對局快照；房間與對局都不存在，已保存的大廳狀態改為空桌。
     *
     * @param tableId 要清除狀態的桌子 UUID。
     */
    fun applySnapshotCleared(tableId: Uuid) {
        updateTable(tableId) { current ->
            current.withGameSnapshot(null).copy(
                roomSnapshot = null,
                roundPreparationSnapshot = null,
                handReadinessAnalysis = null,
                tableOccupancy = current.tableOccupancy?.copy(
                    occupancy = TableOccupancyDto.VACANT,
                    roomSnapshot = null,
                    playingPlayerIds = emptyList(),
                    playingAiPlayerIds = emptyList(),
                    playingGameConfig = null,
                ),
            )
        }
    }

    /**
     * 移除一張已不存在的麻將桌及其所有桌級 read-side 狀態。
     *
     * 與 [applySnapshotCleared] 不同，這裡不保留空桌 occupancy；同一桌子識別碼日後重新出現時，
     * 必須由新的占用或快照 payload 重新建立完整狀態。
     *
     * @param tableId 要移除的桌子 UUID。
     */
    fun removeTable(tableId: Uuid) {
        tables.remove(tableId)
    }

    /**
     * 保存沒有伴隨遊戲動作的主動同步快照；[aiPlayerIds] 為該對局由 AI 操控的玩家。
     *
     * @param gameId 對局／桌子 UUID。
     * @param snapshot 對局狀態快照，識別碼須與 [gameId] 相同。
     * @param aiPlayerIds 由 AI 操控的玩家 UUID。
     * @param roundPreparation 開局準備快照；沒有時為 `null`。
     * @param handReadinessAnalysis 目前手牌分析；沒有時為 `null`。
     */
    fun applyGameSnapshot(
        gameId: Uuid,
        snapshot: TableStateSnapshot,
        aiPlayerIds: Set<Uuid>,
        roundPreparation: RoundPreparationSnapshot? = null,
        handReadinessAnalysis: HandReadinessAnalysisDto? = null,
    ) {
        require(snapshot.id == gameId) { "Game snapshot ID does not match its payload ID." }
        updateTable(gameId) { current ->
            val waitingConfig = current.roomSnapshot?.gameConfig?.toDto(networkRegistries)
            current.withGameSnapshot(snapshot, aiPlayerIds).copy(
                tableOccupancy = current.tableOccupancy?.copy(
                    occupancy = TableOccupancyDto.GAME,
                    playingGameConfig = current.tableOccupancy.playingGameConfig ?: waitingConfig,
                ),
                roundPreparationSnapshot = roundPreparation,
                handReadinessAnalysis = handReadinessAnalysis,
                roomSnapshot = null,
            )
        }
    }

    /**
     * 以轉換函式更新單一桌子的 read-side 狀態。
     *
     * @param tableId 要更新的桌子 UUID。
     * @param transform 由目前狀態產生新狀態的轉換函式。
     */
    private fun updateTable(tableId: Uuid, transform: (ClientTableState) -> ClientTableState) {
        tables[tableId] = transform(tables[tableId] ?: ClientTableState())
    }

    /** 清除離開伺服器後不再有效的 client-side 狀態。 */
    fun clear() {
        tables.clear()
    }
}
