package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.TilePoseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallProjectionContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallSideLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableArrangementState
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileArrangementInput
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import kotlin.uuid.Uuid

/**
 * 依牌譜保存的桌況擺出整桌的牌，與對局中用同一套擺牌計算。
 *
 * 牌譜不保存牌與玩家的 UUID，擺牌時以局內牌索引與座位產生固定的識別碼，只用來對應牌與牌列。
 */
object HistoryReplayArrangement {
    /**
     * 擺出整桌的牌，桌子的控制方塊在原點、朝北。
     *
     * @param state 牌譜保存的桌況。
     * @param notes 牌譜保存的擺放附註。
     * @param module 這場對局的規則模組，提供牌牆的墩數與桌邊對應。
     * @param tileLayout 規則的桌面牌位配置。
     * @param openHands 是否把每一家的手牌面朝上攤開，與結算攤牌用同一套擺法；否則照保存的姿態。
     * @param handPoses 取代上述姿態的手牌姿態，依局內牌索引，例如胡牌倒牌與流局蓋牌。
     * @param sidewaysMarker 牌河標記是否代表橫放。
     * @return 依局內牌索引排列的擺放；沒有桌面位置的牌（例如沒有實體牌牆時的牌牆牌）不列入。
     */
    fun arrange(
        state: HistoryRoundStateDto,
        notes: ReplayTableNotes,
        module: MahjongRuleModule<*>,
        tileLayout: TableTileLayoutProfile,
        openHands: Boolean = false,
        handPoses: Map<Int, TilePose> = emptyMap(),
        sidewaysMarker: (String) -> Boolean,
    ): Map<Int, ArrangedTile> {
        val playerCount = state.players.size
        // 牌譜的莊家以初始座位記錄，擺牌使用玩家在桌況中的位置。
        val dealerIndex = state.players.indexOfFirst { it.initialSeatIndex == state.dealerSeat }
        val stacksPerSide = MahjongTileTableLayout.wallStacksPerSide(module, playerCount)
        val sideLayout = MahjongTileWallSideLayout.forRule(module, playerCount, dealerIndex)
        val projection = if (stacksPerSide != null && sideLayout != null && state.wallPlacements != null) {
            MahjongTileWallProjectionContext(
                controllerX = 0,
                controllerY = 0,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                sideLayout = sideLayout,
                tileLayout = tileLayout,
                stacksPerSide = stacksPerSide,
            )
        } else {
            null
        }
        val table = arrangementState(state, dealerIndex, notes, sidewaysMarker)
        val wallTiles = (state.wallTiles + state.reservedTiles).toSet()
        val handTiles = state.players.flatMap { it.handTiles + listOfNotNull(it.lastDrawn) }.toSet()
        val arranged = TableTileArrangement.arrange(
            input = TableTileArrangementInput(
                controllerX = 0,
                controllerY = 0,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(playerCount),
                table = table,
                tileLayout = tileLayout,
                cornerWidthBySeat = notes.cornerWidthBySeat,
                handPoseStates = (
                    if (openHands) {
                        handTiles.associateWith { TilePose.FACE_UP }
                    } else {
                        state.tilePoses.filterKeys(handTiles::contains).mapValues { (_, pose) -> pose.toDomain() }
                    } + handPoses.filterKeys(handTiles::contains)
                    ).map { (index, pose) -> TilePoseState(tileId(index), pose) },
                wallProjection = projection,
                wallClearance = projection?.clearance(table.wallPlacements.orEmpty().values + notes.extraWallReach),
                faceUpWallTileIds = state.tilePoses.filter { (index, pose) -> index in wallTiles && pose == TilePoseDto.FACE_UP }.keys.map(::tileId).toSet(),
                // 牌譜回放看得到每一家的牌面，立著的手牌也照桌況中的順序排。
                standingOrderPublic = true,
            ),
        )
        return arranged.entries.associate { (id, tile) -> tileIndex(id) to tile }
    }

    /**
     * 局內牌索引對應的固定識別碼。
     *
     * @param index 局內牌索引。
     * @return 識別碼。
     */
    fun tileId(index: Int): Uuid = Uuid.fromLongs(TILE_ID_HIGH, index.toLong())

    /**
     * 座位對應的固定玩家識別碼。
     *
     * @param seat 座位。
     * @return 識別碼。
     */
    fun playerId(seat: Int): Uuid = Uuid.fromLongs(PLAYER_ID_HIGH, seat.toLong())

    /**
     * 識別碼對應的局內牌索引。
     *
     * @param id 由 [tileId] 產生的識別碼。
     * @return 局內牌索引。
     */
    private fun tileIndex(id: Uuid): Int = id.toLongs { _, low -> low.toInt() }

    /**
     * 把牌譜桌況轉成擺牌用桌況。
     *
     * 牌河從第一張帶有橫放標記的牌起，第一張沒有被鳴走的牌橫放，與對局中的牌河一致。
     *
     * @param state 牌譜保存的桌況。
     * @param dealerIndex 莊家在桌況玩家清單中的位置。
     * @param notes 牌譜保存的擺放附註。
     * @param sidewaysMarker 牌河標記是否代表橫放。
     * @return 擺牌用桌況；玩家依桌況中的順序排列，與對局中相同。
     */
    private fun arrangementState(
        state: HistoryRoundStateDto,
        dealerIndex: Int,
        notes: ReplayTableNotes,
        sidewaysMarker: (String) -> Boolean,
    ): TableArrangementState = TableArrangementState(
        players = state.players.map { player ->
            val markedIndex = player.discards.indexOfFirst { discard -> discard.markers.any(sidewaysMarker) }
            TableArrangementState.Player(
                id = playerId(player.initialSeatIndex),
                standingTileIds = player.handTiles.map(::tileId),
                drawnTileId = player.lastDrawn?.let(::tileId),
                melds = player.melds.map { meld ->
                    MahjongMeldTileGroup(
                        type = meld.type.toDomain(),
                        tileIds = meld.tiles.map(::tileId),
                        calledTileId = meld.sourceTile?.let(::tileId),
                        sourceDirection = meld.sourceDirection?.toDomain() ?: RelativeDirection.Self,
                        allTilesFaceDown = meld.faceDownTileIds.size == meld.tiles.size,
                        faceDownTileIds = meld.faceDownTileIds.map(::tileId).toSet(),
                    )
                },
                setAsideTiles = player.tileAreas.flatMap { area -> area.tiles.map { TableArrangementState.SetAsideTile(tileId(it), area.areaId) } },
                discards = player.discards.map { TableArrangementState.Discard(tileId(it.tile), it.isTaken) },
                sidewaysMarkedTileId = if (markedIndex < 0) null else player.discards.drop(markedIndex).firstOrNull { !it.isTaken }?.tile?.let(::tileId),
            )
        },
        wallTileIds = state.wallTiles.map(::tileId),
        reservedTileIds = state.reservedTiles.map(::tileId),
        wallPlacements = state.wallPlacements?.entries?.associate { (index, placement) -> tileId(index) to placement.toDomain() },
        dealerIndex = dealerIndex,
        discardOrder = state.discardOrder.map(::tileId),
        scatterPatternSeed = notes.scatterPatternSeed ?: 0,
    )

    /** 牌識別碼的高位，與玩家識別碼分開。 */
    private const val TILE_ID_HIGH: Long = 1L

    /** 玩家識別碼的高位。 */
    private const val PLAYER_ID_HIGH: Long = 2L
}
