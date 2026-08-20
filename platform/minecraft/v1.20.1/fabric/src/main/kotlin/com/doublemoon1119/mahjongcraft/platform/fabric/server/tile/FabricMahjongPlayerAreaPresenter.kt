package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
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
    private val tickClock: FabricTickMonotonicClock,
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
     * 依 [MahjongInitialDealPresentation.dealBatchSizes] 分輪，每一輪依序（不是同時）輪流對每個座位
     * 各排定一次「起飛→落下」動畫——莊家先抓一批，換下一家抓一批，輪完一圈才回到莊家抓下一批，跟
     * [GameInitializer] 實際發牌的輪轉順序一致（座位輪轉順序同樣固定從
     * [MahjongInitialDealPresentation.dealerSeatIndex] 開始）；「輪次×座位」攤平成一個全域循序索引
     * 餵給 [MahjongTileTableLayout.dealBatchStartDelayTicks]，讓每一次抓取（不論哪個座位）都完整
     * 播完才輪到下一次，理由同 [MahjongPlayerAreaPresenter.presentInitialDeal] KDoc。
     * 批次內每張牌各自的世界起點就是牌牆生成時留下的既有位置（不重新查詢牌山結構座標，直接讀 entity
     * 目前的實際座標），批次終點是 [MahjongTileTableLayout.handPlacement] 算出的最終手牌格位——跟
     * [present] 一樣不建立新 entity。
     *
     * 全部座位的最後一次抓取都落地後，額外統一排定一次翻牌動畫（[scheduleDealFlipAnimation]）——所有
     * 已成功領走的牌（不論哪一批、哪個座位）同一時間一起原地翻起，觸發時機由
     * [MahjongTileTableLayout.dealFlipStartDelayTicks] 依總抓取次數算出。
     */
    override fun presentInitialDeal(presentation: MahjongInitialDealPresentation): MahjongPlayerAreaPresentationResult {
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
                    "presentInitialDeal tableId={} tileId={} skipped: no existing wall entity found to claim",
                    presentation.tableId,
                    tileId,
                )
            }
            return tile
        }

        val seatCount = presentation.handTileIdsBySeatIndex.size
        val dealOrderSeatIndices = List(seatCount) { offset -> (presentation.dealerSeatIndex + offset) % seatCount }
        val cornerYieldShiftBySeat = presentation.handTileIdsBySeatIndex.mapValues { (seatIndex, tileIds) ->
            val comboStickCount = if (seatIndex == presentation.dealerSeatIndex) presentation.comboStickCount else 0
            MahjongTileTableLayout.handCornerYieldShift(
                tileIds.size,
                MahjongTileTableLayout.stickAreaWidth(comboStickCount),
                hasDrawnTile = false,
            )
        }

        val dealtTiles = mutableListOf<MahjongTileEntity>()
        var batchStart = 0
        presentation.dealBatchSizes.forEachIndexed { batchIndex, batchSize ->
            dealOrderSeatIndices.forEachIndexed { turnOffset, seatIndex ->
                val tileIds = presentation.handTileIdsBySeatIndex.getValue(seatIndex)
                val globalTurnIndex = batchIndex * seatCount + turnOffset
                val batchDelayTicks = MahjongTileTableLayout.dealBatchStartDelayTicks(globalTurnIndex)
                tileIds.drop(batchStart).take(batchSize).forEachIndexed { indexInBatch, tileId ->
                    val tile = claimTile(tileId) ?: return@forEachIndexed
                    val tileIndex = batchStart + indexInBatch
                    val placement = MahjongTileTableLayout.handPlacement(
                        controllerX = controllerPos.x,
                        controllerY = controllerPos.y,
                        controllerZ = controllerPos.z,
                        tableFacing = presentation.tableFacing,
                        seatIndex = seatIndex,
                        handSize = tileIds.size,
                        tileIndex = tileIndex,
                        cornerYieldShift = cornerYieldShiftBySeat.getValue(seatIndex),
                    )
                    tile.assignToTable(presentation.tableId)
                    scheduleDealBatchAnimation(tile, placement, batchDelayTicks)
                    dealtTiles += tile
                }
            }
            batchStart += batchSize
        }

        val totalTurnCount = presentation.dealBatchSizes.size * seatCount
        scheduleDealFlipAnimation(dealtTiles, MahjongTileTableLayout.dealFlipStartDelayTicks(totalTurnCount))

        table.markDirty()
        if (missingTileCount > 0) {
            logger.warn(
                "presentInitialDeal tableId={} presented with {} missing tile(s)",
                presentation.tableId,
                missingTileCount,
            )
            return MahjongPlayerAreaPresentationResult.SPAWN_FAILED
        }
        return MahjongPlayerAreaPresentationResult.PRESENTED
    }

    /**
     * 排定單張牌「起飛→落下」兩階段動畫；兩階段的實際設置（`refreshPositionAndAngles`／
     * `startMotionAnimation`）都透過 [tickClock] 延遲到真正輪到執行的那一刻才呼叫，[batchDelayTicks]
     * 到期前完全不碰這張牌——牌在牆上的既有 entity 本來就已經是可見狀態，不需要、也不該提早改動它的
     * 動畫欄位。
     *
     * 這跟牌牆生成掉落動畫的做法刻意不同：掉落動畫是幫「剛生成、還沒真正出現過」的 entity 排定未來
     * 才開始的動畫，此時 entity 已存在但邏輯座標已經是掉落終點，需要靠 renderer 端的「延遲隱形」機制
     * 避免提早顯示成飄在半空的錯誤畫面；發牌抓取則是幫「已經存在、正常顯示在牌牆上」的 entity 排定
     * 動畫，如果同樣提早設定 `animating`（即使 `startGameTime` 訂在未來），會被同一套「延遲隱形」
     * 機制誤判成尚未到期而跟著隱形，導致牌在真正起飛前反而先消失——這正是這裡不能沿用掉落動畫那種
     * 「一次全部同步排定、靠隱形機制擋住提早顯示」寫法的原因，只能靠真的延遲呼叫本身來達到「輪到才
     * 動」的效果。
     *
     * 起飛終點高度（[peakY]）是這張牌牌牆原位高度（[wallY]）加上 [DEAL_LIFT_HEIGHT]，不是統一對齊到
     * `finalPlacement.y`——同一批兩敦牌各自的上下兩層原本高度就不同（見 `MahjongTileTableLayout`
     * `layer` 疊高機制），若起飛終點固定用同一個絕對高度，上下兩層會在起飛途中收斂到同一個高度，看起來
     * 像下層那張牌憑空消失；改成「相對自己原高度往上抬固定量」，兩層之間的相對高度差在起飛階段維持
     * 不變，落下階段的起點沿用各自的 [peakY]，兩階段共用同一個「頂點」，符合「高度保持不變」的設計。
     *
     * 起飛播完的那一刻，隱形與瞬間重新排列到手牌列上空是同一個瞬間發生（先隱形、緊接著同一個 tick
     * 內就 `refreshPositionAndAngles` 過去，中間不留任何一幀「隱形但還沒換位置」的過渡狀態）；接著
     * 額外維持 [MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS] 的隱形（沿用 vanilla `Entity.isInvisible`，
     * 見 `MahjongTileEntityRenderer` 的顯示端判斷），讓「重新排成一列」感覺像是刻意的一個轉場動作，
     * 不是無縫瞬移；隱形時間一到才解除隱形、開始播放落下動畫，讓玩家看到的是「消失（同時已經換到新
     * 位置）→短暫看不見→在新位置重新出現、開始下落」。
     */
    private fun scheduleDealBatchAnimation(
        tile: MahjongTileEntity,
        finalPlacement: MahjongTileWallPlacement,
        batchDelayTicks: Int,
    ) {
        tickClock.scheduleAfter(batchDelayTicks * MILLIS_PER_TICK) {
            if (tile.isRemoved) return@scheduleAfter
            val peakY = tile.y + DEAL_LIFT_HEIGHT
            val wallX = tile.x
            val wallY = tile.y
            val wallZ = tile.z
            tile.refreshPositionAndAngles(wallX, peakY, wallZ, tile.yaw, 0.0f)
            tile.startMotionAnimation(
                startGameTime = tile.world.time,
                durationTicks = MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS,
                arcHeight = 0.0,
                startOffset = DiceAnimationVector(0.0, wallY - peakY, 0.0),
                startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
            )

            tickClock.scheduleAfter(MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS * MILLIS_PER_TICK) {
                if (tile.isRemoved) return@scheduleAfter
                tile.isInvisible = true
                tile.refreshPositionAndAngles(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw, 0.0f)

                tickClock.scheduleAfter(MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS * MILLIS_PER_TICK) {
                    if (tile.isRemoved) return@scheduleAfter
                    tile.startMotionAnimation(
                        startGameTime = tile.world.time,
                        durationTicks = MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS,
                        arcHeight = 0.0,
                        startOffset = DiceAnimationVector(0.0, peakY - finalPlacement.y, 0.0),
                        startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                        endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    )
                    tile.tilePose = MahjongTilePose.FACE_DOWN
                    tile.isInvisible = false
                }
            }
        }
    }

    /**
     * 排定開局發牌動畫最後統一的翻牌動畫：所有座位、所有批次成功領到的牌（[tiles]）在同一個 tick 一起
     * 原地翻起——姿態從 [MahjongTilePose.FACE_DOWN] 轉 [MahjongTilePose.STANDING]，位置完全不動
     * （[com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector] 起點偏移固定
     * `(0, 0, 0)`），只有姿態旋轉角隨動畫進度內插，理由見 [presentInitialDeal] KDoc。
     */
    private fun scheduleDealFlipAnimation(tiles: List<MahjongTileEntity>, flipDelayTicks: Int) {
        tickClock.scheduleAfter(flipDelayTicks * MILLIS_PER_TICK) {
            tiles.forEach { tile ->
                if (tile.isRemoved) return@forEach
                tile.startMotionAnimation(
                    startGameTime = tile.world.time,
                    durationTicks = MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffset = DiceAnimationVector(0.0, 0.0, 0.0),
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                )
                tile.tilePose = MahjongTilePose.STANDING
            }
        }
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

        /** 開局發牌動畫起飛階段的相對高度，起始估算值，預期進遊戲後調整。 */
        const val DEAL_LIFT_HEIGHT: Double = 0.4

        /** Minecraft 正常運行時每個 tick 對應的毫秒數（20 TPS），換算 [FabricTickMonotonicClock.scheduleAfter] 的延遲毫秒數用。 */
        const val MILLIS_PER_TICK: Long = 50L
    }
}
