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
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationResult
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
     *    這個平移量，逐張擺放立牌與摸牌位（[MahjongTilePose.STANDING]）——摸牌位在
     *    [MahjongPlayerAreaPresentation.animateDrawnTile] 為 `true` 時改排定
     *    [TileAnimationSteps.scheduleDrawnTile]，不直接定格。[MahjongPlayerAreaPresentation.standingTileIds] 的
     *    順序是加入手牌的時間軸（先加入的在前，見 `Hand.discardById` KDoc），不是畫面左右順序，這裡
     *    刻意把它反過來對應 [MahjongTileTableLayout.handPlacement] 的 `tileIndex`（`0` 是玩家自己右手
     *    邊）——最後加入立牌的那張牌（例如非摸切捨牌時併入的 `lastDrawn`）因此會落在最右手邊，符合
     *    真實麻將摸牌後插入手牌的直覺方向。
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

        presentation.standingTileIds.forEachIndexed { orderIndex, tileId ->
            val tile = claimTile(tileId) ?: return@forEachIndexed
            val placement = MahjongTileTableLayout.handPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.seatIndex,
                handSize = presentation.standingTileIds.size,
                tileIndex = presentation.standingTileIds.size - 1 - orderIndex,
                cornerYieldShift = cornerYieldShift,
            )
            tile.assignToTable(presentation.tableId)
            tile.tilePose = MahjongTilePose.STANDING
            tile.teleportExistingManagedTile(placement)
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
                if (presentation.animateDrawnTile) {
                    // 起飛前維持牌現在的既有姿態（牌牆生成以來一直是 MahjongTilePose.FACE_DOWN，還沒被
                    // 動過），不要在這裡先改成 STANDING——姿態要等 TileAnimationSteps.scheduleDrawnTile 隱形傳送
                    // 那一刻才切換，見該方法 KDoc。
                    TileAnimationSteps.scheduleDrawnTile(tile, placement)
                } else {
                    tile.tilePose = MahjongTilePose.STANDING
                    tile.teleportExistingManagedTile(placement)
                }
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
            if (tileId in presentation.animatedMeldClaimTileIds) {
                TileAnimationSteps.scheduleMeldClaim(tile, placement, pose)
            } else {
                tile.tilePose = pose
                tile.teleportExistingManagedTile(placement)
            }
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
                placeMeldTile(
                    tileAtSlot[slot],
                    cursorAlong,
                    isSidewaysTile = isSideways,
                    pose = closedKanPose(meld, slot, slotCount),
                )
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
     * 依序完成三件事：
     * 1. 強制理牌重排——不論贏家原本是否啟用自動整理手牌，把
     *    [MahjongWinCelebrationPresentation.organizedStandingTileIds]（呼叫端已經算好的整理後目標順序）
     *    裡每一張牌都排定一次「從目前位置飛到整理後格位」的動畫（[TileAnimationSteps.scheduleReorder]）；
     *    自摸胡牌張保留在 [MahjongTileTableLayout.drawnTilePlacement] 的右側間隔位置，其餘牌使用
     *    [MahjongTileTableLayout.handPlacement]，
     *    全部共用同一個絕對起訖時刻（[reorderStartGameTime]／`reorderEndGameTime`），沒有移動的牌也照樣
     *    播放（起訖位置相同），維持這批牌步調一致的既有慣例。
     * 2. 自摸（[MahjongWinCelebrationPresentation.isTsumo]）才有的中繼步驟：自摸牌
     *    （[MahjongWinCelebrationPresentation.winningTileId]，此時已經是 [organizedStandingTileIds] 之
     *    一）單獨倒下（[TileAnimationSteps.scheduleLaydown]），姿態從立牌轉平放牌面朝上，位置不變。
     * 3. 其餘立牌（自摸時排除自摸牌，榮和／搶槓時是全部）在共用的絕對時刻一起倒下——自摸要等自摸牌
     *    倒下播完、再等 [MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS]；榮和／搶槓省略自摸牌
     *    那一步，直接從理牌重排播完後等待同一個常數。
     *
     * 找不到對應 UUID 的既有 entity 時該筆直接跳過並記警告 log，不中斷其餘牌的呈現，比照 [present]
     * 既有的 best-effort 慣例；[MahjongWinCelebrationPresentation.winningTileId] 找不到既有 entity 時，
     * 沒有任何一張牌可以當作降臨特效的目標，[MahjongWinCelebrationResult.handLaydownEndGameTime] 回傳
     * `null`，呼叫端不應接續排定特效。
     *
     * 不計算降臨特效本身——那是呼叫端（`FabricGamePresentationPublisher`）拿到
     * [MahjongWinCelebrationResult.handLaydownEndGameTime] 之後，另外委託
     * `FabricWinCelebrationEffectScheduler` 處理的職責，理由見該類別 KDoc。
     */
    override fun presentWinCelebration(presentation: MahjongWinCelebrationPresentation): MahjongWinCelebrationResult {
        val world = resolveWorld(presentation.tableLocation)
            ?: return MahjongWinCelebrationResult(MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND, null)
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongWinCelebrationResult(MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND, null)
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongWinCelebrationResult(MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND, null)
        }

        var missingTileCount = 0

        fun claimTile(tileId: Uuid): MahjongTileEntity? {
            val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity
            if (tile == null) {
                missingTileCount++
                logger.warn(
                    "publishWinCelebration tableId={} tileId={} skipped: no existing managed tile entity found to claim",
                    presentation.tableId,
                    tileId,
                )
            }
            return tile
        }

        val reservedCornerWidth =
            MahjongTileTableLayout.stickAreaWidth(presentation.comboStickCount) +
                MahjongTileTableLayout.meldAreaWidth(presentation.melds)
        val standingHandTileIds = if (presentation.isTsumo) {
            presentation.organizedStandingTileIds.filterNot { tileId -> tileId == presentation.winningTileId }
        } else {
            presentation.organizedStandingTileIds
        }
        val cornerYieldShift = MahjongTileTableLayout.handCornerYieldShift(
            standingHandTileIds.size,
            reservedCornerWidth,
            hasDrawnTile = presentation.isTsumo,
        )

        val reorderStartGameTime = world.time
        val reorderEndGameTime = reorderStartGameTime + MahjongTileTableLayout.WIN_REORDER_FLIGHT_DURATION_TICKS

        val claimedStandingTiles = linkedMapOf<Uuid, MahjongTileEntity>()
        standingHandTileIds.forEachIndexed { orderIndex, tileId ->
            val tile = claimTile(tileId) ?: return@forEachIndexed
            claimedStandingTiles[tileId] = tile
            val placement = MahjongTileTableLayout.handPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.seatIndex,
                handSize = standingHandTileIds.size,
                tileIndex = standingHandTileIds.size - 1 - orderIndex,
                cornerYieldShift = cornerYieldShift,
            )
            tile.assignToTable(presentation.tableId)
            TileAnimationSteps.scheduleReorder(tile, placement, reorderStartGameTime)
        }

        val winningTile = if (presentation.isTsumo) {
            claimTile(presentation.winningTileId)?.also { tile ->
                claimedStandingTiles[presentation.winningTileId] = tile
                val placement = MahjongTileTableLayout.drawnTilePlacement(
                    controllerX = controllerPos.x,
                    controllerY = controllerPos.y,
                    controllerZ = controllerPos.z,
                    tableFacing = presentation.tableFacing,
                    seatIndex = presentation.seatIndex,
                    standingTileCount = standingHandTileIds.size,
                    cornerYieldShift = cornerYieldShift,
                )
                tile.assignToTable(presentation.tableId)
                TileAnimationSteps.scheduleReorder(tile, placement, reorderStartGameTime)
            }
        } else {
            claimTile(presentation.winningTileId)
        }

        val handLaydownEndGameTime = winningTile?.let {
            if (presentation.isTsumo) {
                val winTileLaydownStartGameTime = reorderEndGameTime
                val winTileLaydownEndGameTime = winTileLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
                TileAnimationSteps.scheduleLaydown(winningTile, winTileLaydownStartGameTime)

                val restLaydownStartGameTime = winTileLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
                (claimedStandingTiles - presentation.winningTileId).values.forEach { tile ->
                    TileAnimationSteps.scheduleLaydown(tile, restLaydownStartGameTime)
                }
                restLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            } else {
                val handLaydownStartGameTime = reorderEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
                claimedStandingTiles.values.forEach { tile -> TileAnimationSteps.scheduleLaydown(tile, handLaydownStartGameTime) }
                handLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            }
        }

        table.markDirty()
        val result = if (missingTileCount > 0) {
            logger.warn(
                "publishWinCelebration tableId={} presented with {} missing tile(s)",
                presentation.tableId,
                missingTileCount,
            )
            MahjongPlayerAreaPresentationResult.SPAWN_FAILED
        } else {
            MahjongPlayerAreaPresentationResult.PRESENTED
        }
        return MahjongWinCelebrationResult(result, handLaydownEndGameTime)
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
     * [present] 一樣不建立新 entity，也一樣把 [MahjongInitialDealPresentation.handTileIdsBySeatIndex]
     * 各座位清單內的順序（時間軸，非畫面左右順序）反過來對應 `tileIndex`，見 [present] KDoc。
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
        // 翻牌後最終該停在哪一格改看 postFlipHandTileIdsBySeatIndex（自動整理手牌啟用時是整理過的
        // 順序），跟決定發牌動畫本身節奏的 handTileIdsBySeatIndex 分開算，見
        // MahjongInitialDealPresentation KDoc。
        val postFlipOrderIndexBySeat = presentation.postFlipHandTileIdsBySeatIndex.mapValues { (_, tileIds) ->
            tileIds.withIndex().associate { (index, tileId) -> tileId to index }
        }

        val totalTurnCount = presentation.dealBatchSizes.size * seatCount
        // 所有牌共用同一個算好的絕對翻牌時刻，不靠每張牌各自用減法反推剩餘等待時間湊出同一個目標，
        // 理由見 AnimationStep.WaitUntil KDoc。
        val flipAbsoluteGameTime = world.time + presentation.extraLeadDelayTicks +
            MahjongTileTableLayout.dealFlipStartDelayTicks(totalTurnCount)
        var batchStart = 0
        presentation.dealBatchSizes.forEachIndexed { batchIndex, batchSize ->
            dealOrderSeatIndices.forEachIndexed { turnOffset, seatIndex ->
                val tileIds = presentation.handTileIdsBySeatIndex.getValue(seatIndex)
                val globalTurnIndex = batchIndex * seatCount + turnOffset
                // 絕對時刻，不是相對等待——這批（同一次抓的所有牌）疊加進去的既有佇列可能還殘留著牌牆
                // 掉落動畫沒播完的 step（見 AnimationStep.WaitUntil KDoc），如果用相對 Wait 表達，殘留
                // 得比較久的牌會被拖慢、晚起飛，導致同一次抓的牌看起來分批起飛而不是一起。
                val liftAbsoluteGameTime = world.time + presentation.extraLeadDelayTicks +
                    MahjongTileTableLayout.dealBatchStartDelayTicks(globalTurnIndex)
                tileIds.drop(batchStart).take(batchSize).forEachIndexed { indexInBatch, tileId ->
                    val tile = claimTile(tileId) ?: return@forEachIndexed
                    val orderIndex = batchStart + indexInBatch
                    val placement = MahjongTileTableLayout.handPlacement(
                        controllerX = controllerPos.x,
                        controllerY = controllerPos.y,
                        controllerZ = controllerPos.z,
                        tableFacing = presentation.tableFacing,
                        seatIndex = seatIndex,
                        handSize = tileIds.size,
                        tileIndex = tileIds.size - 1 - orderIndex,
                        cornerYieldShift = cornerYieldShiftBySeat.getValue(seatIndex),
                    )
                    val postFlipOrderIndex = postFlipOrderIndexBySeat.getValue(seatIndex).getValue(tileId)
                    val postFlipPlacement = MahjongTileTableLayout.handPlacement(
                        controllerX = controllerPos.x,
                        controllerY = controllerPos.y,
                        controllerZ = controllerPos.z,
                        tableFacing = presentation.tableFacing,
                        seatIndex = seatIndex,
                        handSize = tileIds.size,
                        tileIndex = tileIds.size - 1 - postFlipOrderIndex,
                        cornerYieldShift = cornerYieldShiftBySeat.getValue(seatIndex),
                    )
                    tile.assignToTable(presentation.tableId)
                    TileAnimationSteps.scheduleDealBatch(tile, placement, postFlipPlacement, liftAbsoluteGameTime, flipAbsoluteGameTime)
                }
            }
            batchStart += batchSize
        }

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
