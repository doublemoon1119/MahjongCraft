package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.block.BlockState
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 使用 Fabric 1.20.1 entity 呈現正式手牌／摸牌位／副露：把牌牆管理中的既有 entity 領走並移動，不重新
 * 生成。合併原本各自獨立的 hand-tiles／meld presenter，理由見
 * [MahjongPlayerAreaPresenter] KDoc——手牌讓開副露／積棒的偏移量，只有在同一次呼叫裡同時拿到立牌、
 * 摸牌、副露、積棒支數四種狀態才算得出來。
 */
@Single(binds = [MahjongPlayerAreaPresenter::class])
class FabricMahjongPlayerAreaPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongPlayerAreaPresenter {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 依序完成三件事：
     * 1. 算出副露＋積棒（[MahjongPlayerAreaPresentation.comboStickCount]，只用來算寬度，不管理積棒
     *    entity 本身）總共佔用的寬度（[MahjongTileTableLayout.meldAreaWidth]／
     *    [MahjongTileTableLayout.stickAreaWidth]），換算成整排立牌／摸牌位需要往玩家自己方向平移的
     *    距離（[MahjongTileTableLayout.handCornerYieldShift]）。
     * 2. 用 [MahjongTileTableLayout.handPlacement]／[MahjongTileTableLayout.drawnTilePlacement] 帶著
     *    這個平移量，逐張擺放立牌與摸牌位（[MahjongTilePose.STANDING]）。
     * 3. 用 [MahjongTileTableLayout.meldPlacement] 逐格擺放副露——邏輯照搬原本
     *    `FabricMahjongMeldPresenter.present()`，起始游標從 [MahjongTileTableLayout.stickAreaWidth]
     *    開始（讓副露自然接在積棒外緣），其餘不變，含 [closedKanPose]／加槓 depth-offset。
     *
     * 找不到對應 UUID 的既有 entity 時該筆直接跳過並記警告 log，不中斷其餘牌的呈現，比照本介面
     * best-effort 的既有慣例。
     */
    override fun present(presentation: MahjongPlayerAreaPresentation): MahjongPlayerAreaPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND
        }

        var missingTileCount = 0

        fun claimTile(tileId: Uuid): MahjongTileEntity? {
            val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity
            if (tile == null) {
                missingTileCount++
                logger.warn(
                    "publishPlayerAreaUpdated tableId={} tileId={} skipped: no existing wall entity found to claim",
                    presentation.tableId,
                    tileId,
                )
            }
            return tile
        }

        val reservedCornerWidth =
            MahjongTileTableLayout.stickAreaWidth(presentation.comboStickCount) +
                MahjongTileTableLayout.meldAreaWidth(presentation.melds)
        val cornerYieldShift = MahjongTileTableLayout.handCornerYieldShift(
            presentation.standingTileIds.size,
            reservedCornerWidth,
            hasDrawnTile = presentation.drawnTileId != null,
        )

        presentation.standingTileIds.forEachIndexed { tileIndex, tileId ->
            val tile = claimTile(tileId) ?: return@forEachIndexed
            val placement = MahjongTileTableLayout.handPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.seatIndex,
                handSize = presentation.standingTileIds.size,
                tileIndex = tileIndex,
                cornerYieldShift = cornerYieldShift,
            )
            tile.assignToTable(presentation.tableId)
            tile.tilePose = MahjongTilePose.STANDING
            tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        }

        presentation.drawnTileId?.let { drawnTileId ->
            val tile = claimTile(drawnTileId)
            if (tile != null) {
                val placement = MahjongTileTableLayout.drawnTilePlacement(
                    controllerX = controllerPos.x,
                    controllerY = controllerPos.y,
                    controllerZ = controllerPos.z,
                    tableFacing = presentation.tableFacing,
                    seatIndex = presentation.seatIndex,
                    standingTileCount = presentation.standingTileIds.size,
                    cornerYieldShift = cornerYieldShift,
                )
                tile.assignToTable(presentation.tableId)
                tile.tilePose = MahjongTilePose.STANDING
                tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            }
        }

        fun placeMeldTile(
            tileId: Uuid,
            alongOffsetFromCorner: Double,
            isSidewaysTile: Boolean,
            depthOffsetFromEdge: Double = 0.0,
            pose: MahjongTilePose = MahjongTilePose.FACE_UP,
        ) {
            val tile = claimTile(tileId) ?: return
            val placement = MahjongTileTableLayout.meldPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.seatIndex,
                alongOffsetFromCorner = alongOffsetFromCorner,
                isSidewaysTile = isSidewaysTile,
                depthOffsetFromEdge = depthOffsetFromEdge,
            )
            tile.assignToTable(presentation.tableId)
            tile.tilePose = pose
            tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        }

        var cursorAlong = MahjongTileTableLayout.stickAreaWidth(presentation.comboStickCount)
        presentation.melds.forEachIndexed { meldIndex, meld ->
            if (meldIndex > 0) cursorAlong += MahjongTileTableLayout.MELD_GROUP_GAP
            val addedTileId = if (meld.type == MeldType.ADDED_KAN) meld.tileIds.last() else null
            val baseTileIds = if (addedTileId != null) meld.tileIds.dropLast(1) else meld.tileIds
            val slotCount = baseTileIds.size
            val sidewaysSlot = meld.calledTileId?.let { MahjongTileTableLayout.sidewaysSlotIndex(meld.sourceDirection, slotCount) }
            val remainingTileIds = ArrayDeque(baseTileIds.filterNot { it == meld.calledTileId })
            val tileAtSlot = (0 until slotCount).map { slot ->
                if (slot == sidewaysSlot) meld.calledTileId!! else remainingTileIds.removeFirst()
            }
            var sidewaysAlongOffset: Double? = null
            for (slot in slotCount - 1 downTo 0) {
                val isSideways = slot == sidewaysSlot
                val halfWidth =
                    if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
                cursorAlong += halfWidth
                placeMeldTile(tileAtSlot[slot], cursorAlong, isSidewaysTile = isSideways, pose = closedKanPose(meld, slot, slotCount))
                if (isSideways) sidewaysAlongOffset = cursorAlong
                cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            }
            if (addedTileId != null) {
                placeMeldTile(
                    addedTileId,
                    sidewaysAlongOffset!!,
                    isSidewaysTile = true,
                    depthOffsetFromEdge = MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET,
                )
            }
        }

        table.markDirty()
        if (missingTileCount > 0) {
            logger.warn(
                "publishPlayerAreaUpdated tableId={} presented with {} missing tile(s)",
                presentation.tableId,
                missingTileCount,
            )
            return MahjongPlayerAreaPresentationResult.SPAWN_FAILED
        }
        return MahjongPlayerAreaPresentationResult.PRESENTED
    }

    /**
     * 換算暗槓（[MeldType.CLOSED_KAN]）組內第 [slot] 格該用的姿態；非暗槓固定
     * [MahjongTilePose.FACE_UP]。[MahjongMeldTileGroup.allTilesFaceDown] 為 `true`（該規則暗槓完全不
     * 公開）時四張全部蓋牌；為 `false`（該規則暗槓身份公開）時只蓋組內第一、最後一格（兩端），中間
     * 攤牌，比照真實麻將暗槓的傳統擺法。
     */
    private fun closedKanPose(meld: MahjongMeldTileGroup, slot: Int, slotCount: Int): MahjongTilePose {
        if (meld.type != MeldType.CLOSED_KAN) return MahjongTilePose.FACE_UP
        if (meld.allTilesFaceDown) return MahjongTilePose.FACE_DOWN
        return if (slot == 0 || slot == slotCount - 1) MahjongTilePose.FACE_DOWN else MahjongTilePose.FACE_UP
    }

    /**
     * 清除指定 controller 周圍且 table UUID 相符的所有正式管理中麻將牌——跟
     * [FabricMahjongTileWallPresenter.clear] 效果相同（都是清這張桌子的全部管理中麻將牌，不分子
     * 系統），保留成獨立方法只是維持介面對稱，呼叫端仍可能只想觸發這條路徑的清除。積棒不在這裡清除
     * （見 [MahjongScoringStickPresenter]）。
     */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val tiles = findManagedTiles(world, tableId, tableLocation.toBlockPos())
        tiles.forEach(MahjongTileEntity::discard)
        return tiles.size
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /** 驗證指定位置確實是 UUID 與朝向資料可用的 controller。 */
    private fun resolveTable(
        world: ServerWorld,
        controllerPos: BlockPos,
        state: BlockState,
        tableId: Uuid,
    ): MahjongTableBlockEntity? {
        if (state.block !is MahjongTableBlock || state.get(MahjongTableBlock.PART) != MahjongTablePart.BOTTOM_CENTER) {
            return null
        }
        if (!state.contains(Properties.HORIZONTAL_FACING)) return null
        return (world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity)?.takeIf { table ->
            table.tableId == tableId
        }
    }

    /** 只查詢桌子結構附近並以同步 UUID 精確篩選，避免掃描整個 dimension。 */
    private fun findManagedTiles(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongTileEntity> = world.getEntitiesByClass(
        MahjongTileEntity::class.java,
        Box(controllerPos).expand(TILE_SEARCH_HORIZONTAL, TILE_SEARCH_VERTICAL, TILE_SEARCH_HORIZONTAL),
    ) { tile -> tile.managedTableId == tableId }

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式手牌／副露建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式手牌／副露的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式手牌／副露的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0
    }
}
