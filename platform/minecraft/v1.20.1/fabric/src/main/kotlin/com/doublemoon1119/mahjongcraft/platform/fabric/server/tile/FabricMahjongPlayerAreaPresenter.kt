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
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
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
     *    [scheduleDrawnTileAnimation]，不直接定格。[MahjongPlayerAreaPresentation.standingTileIds] 的
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
                if (presentation.animateDrawnTile) {
                    // 起飛前維持牌現在的既有姿態（牌牆生成以來一直是 MahjongTilePose.FACE_DOWN，還沒被
                    // 動過），不要在這裡先改成 STANDING——姿態要等 scheduleDrawnTileAnimation 隱形傳送
                    // 那一刻才切換，見該方法 KDoc。
                    scheduleDrawnTileAnimation(tile, placement)
                } else {
                    tile.tilePose = MahjongTilePose.STANDING
                    tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
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
                    scheduleDealBatchAnimation(tile, placement, postFlipPlacement, liftAbsoluteGameTime, flipAbsoluteGameTime)
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
     * 排定單張牌「起飛→隱形傳送→落下→（全部座位都到齊後統一）翻牌」整段動畫，一次性組好整個
     * [AnimationStep] 佇列——不再像過去那樣用巢狀 `tickClock.scheduleAfter` 一層層延遲到真正輪到執行
     * 才設置下一階段，改成一次把「這張牌接下來該做的每一件事」都持久化掛在牌自己身上，理由見
     * [AnimatedMahjongEntity] KDoc：後者在 server 關閉時會遺失「接下來該做什麼」的資訊，是牌卡在半空
     * 這個 bug 的根因。
     *
     * [liftAbsoluteGameTime] 是呼叫端算好、同一次抓（同一批、可能橫跨兩敦）所有牌共用同一份的絕對
     * 起飛時刻（[AnimationStep.WaitUntil]），不是相對等待——這批牌被 [tile.enqueueAll] 疊加進去的
     * 既有佇列，此時可能還殘留著牌牆掉落動畫沒播完的 step（`presentInitialDeal` 現在立刻執行，不再
     * 等牌牆掉落動畫全部播完才呼叫，理由見 `AnimatedMahjongEntity` KDoc），如果起飛前的等待用相對
     * `Wait` 表達，殘留得比較久的牌會被拖慢、晚起飛，導致同一次抓的牌看起來分批起飛而不是一起，這是
     * 遊戲內實際發現的問題；[AnimationStep.WaitUntil] 不管佇列殘留多久才真正輪到，一旦輪到就發現目標
     * 時間早就到了、直接繼續，不會被拖慢。
     *
     * 起飛終點高度（`peakY`）是這張牌牌牆原位高度（`wallY`）加上 [DEAL_LIFT_HEIGHT]，不是統一對齊到
     * `finalPlacement.y`——同一批兩敦牌各自的上下兩層原本高度就不同（見 `MahjongTileTableLayout`
     * `layer` 疊高機制），若起飛終點固定用同一個絕對高度，上下兩層會在起飛途中收斂到同一個高度，看起來
     * 像下層那張牌憑空消失；改成「相對自己原高度往上抬固定量」，兩層之間的相對高度差在起飛階段維持
     * 不變，落下階段的起點沿用各自的 `peakY`，兩階段共用同一個「頂點」，符合「高度保持不變」的設計。
     *
     * 起飛播完的那一刻，隱形與瞬間重新排列到手牌列上空是同一個瞬間發生（同一個 tick 內連續處理完
     * [AnimationStep.SetInvisible]／[AnimationStep.Teleport] 兩個瞬間 step，中間不留任何一幀「隱形但
     * 還沒換位置」的過渡狀態）；接著額外維持 [MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS] 的隱形，
     * 讓「重新排成一列」感覺像是刻意的一個轉場動作，不是無縫瞬移；隱形時間一到才解除隱形、開始播放
     * 落下動畫。
     *
     * 翻牌（所有座位、所有批次成功領到的牌同一時間一起原地翻起，姿態從 [MahjongTilePose.FACE_DOWN]
     * 轉 [MahjongTilePose.STANDING]，位置完全不動，只有姿態旋轉角隨動畫進度內插，理由見
     * [presentInitialDeal] KDoc）不再是另外排定的共用觸發點，改成接續在這張牌自己的佇列尾端——
     * [flipAbsoluteGameTime] 是呼叫端（[presentInitialDeal]）算好、所有牌共用同一份的絕對翻牌時刻
     * （[AnimationStep.WaitUntil]），不是每張牌各自用減法反推剩餘等待時間去湊同一個目標，理由見
     * [AnimationStep.WaitUntil] KDoc——用減法反推的舊寫法只要任何一個相關常數改動就可能悄悄失去同步，
     * 且不容易察覺。若這位玩家啟用了自動整理手牌，在觀看緩衝（[OPENING_SEQUENCE_EXTRA_VIEWING_TICKS]）
     * 播完、整段開局呈現真正結束前的最後一刻，才傳送到 [postFlipPlacement]（可能與 [finalPlacement]
     * 不同格）——理由見 [MahjongInitialDealPresentation] KDoc：發牌動畫本身（起飛/落下/翻牌的節奏、
     * 順序）維持原始時間軸不受影響，只有翻牌後最終該停在哪一格才看整理過的順序；刻意等觀看緩衝播完
     * 才校正位置，不是翻牌動畫一結束就立刻校正——翻牌的姿態旋轉剛好收斂、牌還「感覺上」處於剛翻起來
     * 的那個瞬間就整隻手牌重新排列，會讓排序動作跟翻牌動畫黏在一起、像是翻牌還沒真正結束就被打斷，
     * 這是遊戲內實際觀察到的問題。最後額外接上
     * [MahjongTileTableLayout.dealAnimationTicks] 之外、`FabricGamePresentationPublisher` 過去另外
     * 加總的觀看緩衝（[OPENING_SEQUENCE_EXTRA_VIEWING_TICKS]），讓佇列在整段開局呈現真正結束前持續
     * 維持「還在忙」，供 `TablePresentationBusyTracker` 查詢。
     */
    private fun scheduleDealBatchAnimation(
        tile: MahjongTileEntity,
        finalPlacement: MahjongTileWallPlacement,
        postFlipPlacement: MahjongTileWallPlacement,
        liftAbsoluteGameTime: Long,
        flipAbsoluteGameTime: Long,
    ) {
        val wallX = tile.x
        val wallY = tile.y
        val wallZ = tile.z
        val wallYaw = tile.yaw
        val peakY = wallY + DEAL_LIFT_HEIGHT
        val snapGapEndGameTime = liftAbsoluteGameTime + MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS
        val viewingEndGameTime = flipAbsoluteGameTime + MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS +
            OPENING_SEQUENCE_EXTRA_VIEWING_TICKS
        tile.enqueueAll(
            listOf(
                AnimationStep.WaitUntil(liftAbsoluteGameTime),
                AnimationStep.Teleport(wallX, peakY, wallZ, wallYaw),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = wallY - peakY,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                ),
                AnimationStep.SetInvisible(true),
                AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
                AnimationStep.WaitUntil(snapGapEndGameTime),
                AnimationStep.Custom(MahjongTilePose.FACE_DOWN),
                AnimationStep.SetInvisible(false),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = peakY - finalPlacement.y,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                ),
                AnimationStep.WaitUntil(flipAbsoluteGameTime),
                AnimationStep.Custom(MahjongTilePose.STANDING),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                ),
                AnimationStep.WaitUntil(viewingEndGameTime),
                AnimationStep.Teleport(postFlipPlacement.x, postFlipPlacement.y, postFlipPlacement.z, postFlipPlacement.yaw),
            ),
        )
    }

    /**
     * 排定摸牌動畫：跟開局發牌動畫共用同一套「起飛→隱形傳送→落下」節奏
     * （[MahjongTileTableLayout.DRAW_LIFT_HEIGHT]／[MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS]／
     * [MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS]／[MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS]，
     * 手法同 [scheduleDealBatchAnimation]），完整順序是：面朝下起飛→隱形→傳送到摸牌位→（同一瞬間）
     * 姿態換成面向玩家→解除隱形→落下。
     *
     * 跟開局發牌動畫的差別：翻面（[MahjongTilePose.FACE_DOWN] 換成 [MahjongTilePose.STANDING]）不是
     * 落地後另外播放的旋轉動畫，而是在隱形傳送那一刻直接切換姿態——因為切換當下牌本身是隱形的，玩家
     * 看不到姿態瞬間跳變，效果等同「傳送過去的時候順便翻好面」；摸牌是單張動作，不需要像開局發牌那樣
     * 等所有座位都到齊才一起揭曉演出翻牌動畫，直接以面向玩家的姿態落下即可，理由見
     * [MahjongPlayerAreaPresentation.animateDrawnTile] KDoc。起飛與落下兩階段各自的起訖姿態旋轉角相同
     * （不內插），只有隱形傳送那一刻的姿態本身是離散跳變。只有這一張牌，不需要像 [presentInitialDeal]
     * 那樣排定跨座位／跨批次的延遲，起飛立刻開始。
     */
    private fun scheduleDrawnTileAnimation(tile: MahjongTileEntity, finalPlacement: MahjongTileWallPlacement) {
        val wallX = tile.x
        val wallY = tile.y
        val wallZ = tile.z
        val wallYaw = tile.yaw
        val peakY = wallY + MahjongTileTableLayout.DRAW_LIFT_HEIGHT
        val snapGapEndGameTime = tile.world.time + MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS
        tile.enqueueAll(
            listOf(
                AnimationStep.Teleport(wallX, peakY, wallZ, wallYaw),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = wallY - peakY,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                ),
                AnimationStep.SetInvisible(true),
                AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
                AnimationStep.Custom(MahjongTilePose.STANDING),
                AnimationStep.WaitUntil(snapGapEndGameTime),
                AnimationStep.SetInvisible(false),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = peakY - finalPlacement.y,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                ),
            ),
        )
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

        /**
         * 開局發牌動畫全部播完（含翻牌）後，額外掛在每張牌佇列尾端的觀看緩衝，讓桌子在玩家真正看清楚
         * 手牌之前持續維持「還在忙」——過去由 `FabricGamePresentationPublisher` 另外加總進
         * `TablePresentationBusyTracker.markBusyFor` 的時長，現在改成掛在動畫佇列本身尾端，理由見
         * [scheduleDealBatchAnimation] KDoc。
         */
        const val OPENING_SEQUENCE_EXTRA_VIEWING_TICKS: Int = 25
    }
}
