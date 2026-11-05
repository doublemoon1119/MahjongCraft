package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationResult
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 使用 Fabric 1.20.1 entity 呈現正式手牌／摸牌位／副露：把牌牆管理中的既有 entity 領走並移動，不重新
 * 生成。合併原本各自獨立的 hand-tiles／meld presenter，理由見
 * [MahjongPlayerAreaPresenter] KDoc——手牌讓開副露／積棒的偏移量，只有在同一次呼叫裡同時拿到立牌、
 * 摸牌、副露、積棒支數四種狀態才算得出來。
 *
 * @property serverHolder 取得目前伺服器世界與既有麻將牌 entity。
 */
class FabricMahjongPlayerAreaPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongPlayerAreaPresenter {
    private val logger = mahjongCraftLogger(FabricMahjongPlayerAreaPresenter::class)

    /**
     * 依序完成三件事：
     * 1. 強制理牌重排——不論贏家原本是否啟用自動整理手牌，把
     *    [MahjongWinCelebrationPresentation.organizedStandingTileIds]（呼叫端已經算好的整理後目標順序）
     *    裡每一張牌都排定在同一時刻瞬間到達整理後格位（[TileAnimationSteps.scheduleInstantReorder]）；
     *    自摸胡牌張保留在 [MahjongTileTableLayout.drawnTilePlacement] 的右側間隔位置，其餘牌使用
     *    [MahjongTileTableLayout.handPlacement]，
     *    全部共用同一個絕對定位時刻，定位後等待 [MahjongTileTableLayout.WIN_POST_REORDER_DELAY_TICKS] 再開始倒牌流程。
     * 2. 自摸（[MahjongWinCelebrationPresentation.isTsumo]）才有的中繼步驟：自摸牌
     *    （[MahjongWinCelebrationPresentation.winningTileId]，此時已經是 [MahjongWinCelebrationPresentation.organizedStandingTileIds] 之
     *    一）單獨倒下（[TileAnimationSteps.scheduleLaydown]），姿態從立牌轉平放牌面朝上，位置不變。
     * 3. 其餘立牌（自摸時排除自摸牌，榮和／搶槓時是全部）在共用的絕對時刻一起倒下——自摸要等自摸牌
     *    倒下播完、再等 [MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS]；榮和／搶槓省略自摸牌
     *    那一步，直接在整理到位及短暫停頓後等待同一個常數。
     *
     * 找不到對應 UUID 的既有 entity 時該筆直接跳過並記警告 log，不中斷其餘牌的呈現，比照 [present]
     * 既有的 best-effort 慣例；[MahjongWinCelebrationPresentation.winningTileId] 找不到既有 entity 時，
     * 沒有任何一張牌可以當作降臨特效的目標，[MahjongWinCelebrationResult.handLaydownEndGameTime] 回傳
     * `null`，呼叫端不應接續排定特效。
     *
     * 不計算降臨特效本身——那是呼叫端（`FabricGamePresentationPublisher`）拿到
     * [MahjongWinCelebrationResult.handLaydownEndGameTime] 之後，另外委託
     * `FabricWinCelebrationEffectScheduler` 處理的職責，理由見該類別 KDoc。
     *
     * @param presentation 胡牌種類、贏牌與整理後手牌等慶祝呈現資料。
     * @return 呈現狀態，以及成功取得贏牌 entity 時手牌倒下動畫結束時間。
     */
    override fun presentWinCelebration(presentation: MahjongWinCelebrationPresentation): MahjongWinCelebrationResult {
        val world = resolveWorld(presentation.tableLocation)
            ?: return MahjongWinCelebrationResult(MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND, null)
        val controllerPos = presentation.tableLocation.toBlockPos()
        val table = MahjongTableLookup.find(world, controllerPos, presentation.tableId)
            ?: return MahjongWinCelebrationResult(MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND, null)
        if (table.facing != presentation.tableFacing) {
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

        val reservedCornerWidth = MahjongTileTableLayout.cornerAreaWidth(
            cornerPropWidth = presentation.cornerWidth,
            melds = presentation.melds,
            setAsideTileCount = presentation.setAsideTileCount,
        )
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

        // 整條慶祝動畫鏈都由這個起點推導；接在呼叫端指定的時間軸之後，讓同一條時間軸上的連續胡牌
        // 演出依序播放而不重疊（預設 0 等同 world.time，維持既有行為）。
        val reorderStartGameTime = maxOf(world.time, presentation.earliestStartGameTime)
        val reorderEndGameTime = reorderStartGameTime + MahjongTileTableLayout.WIN_POST_REORDER_DELAY_TICKS

        val claimedStandingTiles = linkedMapOf<Uuid, MahjongTileEntity>()
        standingHandTileIds.forEachIndexed { orderIndex, tileId ->
            val tile = claimTile(tileId) ?: return@forEachIndexed
            claimedStandingTiles[tileId] = tile
            val placement = MahjongTileTableLayout.handPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatLayout = presentation.seatLayout,
                seatIndex = presentation.seatIndex,
                handSize = standingHandTileIds.size,
                tileIndex = standingHandTileIds.size - 1 - orderIndex,
                cornerYieldShift = cornerYieldShift,
            )
            tile.assignToTable(presentation.tableId)
            TileAnimationSteps.scheduleInstantReorder(tile, placement, reorderStartGameTime)
        }

        val winningTile = if (presentation.isTsumo) {
            claimTile(presentation.winningTileId)?.also { tile ->
                claimedStandingTiles[presentation.winningTileId] = tile
                val placement = MahjongTileTableLayout.drawnTilePlacement(
                    controllerX = controllerPos.x,
                    controllerY = controllerPos.y,
                    controllerZ = controllerPos.z,
                    tableFacing = presentation.tableFacing,
                    seatLayout = presentation.seatLayout,
                    seatIndex = presentation.seatIndex,
                    standingTileCount = standingHandTileIds.size,
                    cornerYieldShift = cornerYieldShift,
                )
                tile.assignToTable(presentation.tableId)
                TileAnimationSteps.scheduleInstantReorder(
                    tile,
                    placement,
                    reorderStartGameTime,
                )
            }
        } else {
            claimTile(presentation.winningTileId)
        }

        val handLaydownEndGameTime = winningTile?.let {
            if (presentation.isTsumo) {
                val winTileLaydownStartGameTime = reorderEndGameTime
                val winTileLaydownEndGameTime = winTileLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
                TileAnimationSteps.scheduleLaydown(winningTile, winTileLaydownStartGameTime, playGroupSound = true)

                val restLaydownStartGameTime = winTileLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
                val restTiles = (claimedStandingTiles - presentation.winningTileId).values.toList()
                TileAnimationSteps.scheduleGroupLaydown(restTiles, restLaydownStartGameTime)
                restLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            } else {
                val handLaydownStartGameTime = reorderEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
                val handTiles = claimedStandingTiles.values.toList()
                TileAnimationSteps.scheduleGroupLaydown(handTiles, handLaydownStartGameTime)
                handLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            }
        }

        table.blockEntity.markDirty()
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
     * 全部座位的最後一次抓取都落地後，額外統一排定一次翻牌動畫——所有
     * 已成功領走的牌（不論哪一批、哪個座位）同一時間一起原地翻起，觸發時機由
     * [MahjongTileTableLayout.dealFlipStartDelayTicks] 依總抓取次數算出。
     *
     * @param presentation 莊家、各座位發牌批次與最終手牌順序。
     * @return 桌子無效時為 `TABLE_NOT_FOUND`；缺少既有牌時為 `SPAWN_FAILED`；否則為 `PRESENTED`。
     */
    override fun presentInitialDeal(presentation: MahjongInitialDealPresentation): MahjongPlayerAreaPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val table = MahjongTableLookup.find(world, controllerPos, presentation.tableId)
            ?: return MahjongPlayerAreaPresentationResult.TABLE_NOT_FOUND
        if (table.facing != presentation.tableFacing) {
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

        val seatCount = presentation.seatLayout.playerCount
        val dealOrderSeatIndices = List(seatCount) { offset -> (presentation.dealerSeatIndex + offset) % seatCount }
        val cornerYieldShiftBySeat = presentation.handTileIdsBySeatIndex.mapValues { (seatIndex, tileIds) ->
            MahjongTileTableLayout.handCornerYieldShift(
                handSize = tileIds.size,
                reservedCornerWidth = presentation.cornerWidthBySeatIndex[seatIndex] ?: 0.0,
                hasDrawnTile = false,
            )
        }
        // 發牌保留取牌時間順序；翻起後的共享布局只依牌張身分排列，本人的理牌另外在本機呈現。
        val postFlipOrderIndexBySeat = presentation.postFlipHandTileIdsBySeatIndex.mapValues { (_, tileIds) ->
            PublicHandOrder.arrange(tileIds).withIndex().associate { (index, tileId) -> tileId to index }
        }

        val totalTurnCount = presentation.dealBatchSizes.size * seatCount
        // 所有牌共用同一個算好的絕對翻牌時刻，不靠每張牌各自用減法反推剩餘等待時間湊出同一個目標，
        // 理由見 AnimationStep.WaitUntil KDoc。
        val flipAbsoluteGameTime = world.time + presentation.extraLeadDelayTicks +
            MahjongTileTableLayout.dealFlipStartDelayTicks(totalTurnCount)
        var batchStart = 0
        val flipSoundSeatIndices = mutableSetOf<Int>()
        presentation.dealBatchSizes.forEachIndexed { batchIndex, batchSize ->
            dealOrderSeatIndices.forEachIndexed { turnOffset, seatIndex ->
                val tileIds = presentation.handTileIdsBySeatIndex.getValue(seatIndex)
                val globalTurnIndex = batchIndex * seatCount + turnOffset
                // 絕對時刻，不是相對等待——這批（同一次抓的所有牌）疊加進去的既有佇列可能還殘留著牌牆
                // 掉落動畫沒播完的 step（見 AnimationStep.WaitUntil KDoc），如果用相對 Wait 表達，殘留
                // 得比較久的牌會被拖慢、晚起飛，導致同一次抓的牌看起來分批起飛而不是一起。
                val liftAbsoluteGameTime = world.time + presentation.extraLeadDelayTicks +
                    MahjongTileTableLayout.dealBatchStartDelayTicks(globalTurnIndex)
                var dealSoundScheduled = false
                tileIds.drop(batchStart).take(batchSize).forEachIndexed { indexInBatch, tileId ->
                    val tile = claimTile(tileId) ?: return@forEachIndexed
                    val orderIndex = batchStart + indexInBatch
                    val placement = MahjongTileTableLayout.handPlacement(
                        controllerX = controllerPos.x,
                        controllerY = controllerPos.y,
                        controllerZ = controllerPos.z,
                        tableFacing = presentation.tableFacing,
                        seatLayout = presentation.seatLayout,
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
                        seatLayout = presentation.seatLayout,
                        seatIndex = seatIndex,
                        handSize = tileIds.size,
                        tileIndex = tileIds.size - 1 - postFlipOrderIndex,
                        cornerYieldShift = cornerYieldShiftBySeat.getValue(seatIndex),
                    )
                    tile.assignToTable(presentation.tableId)
                    TileAnimationSteps.scheduleDealBatch(
                        tile,
                        placement,
                        postFlipPlacement,
                        liftAbsoluteGameTime,
                        flipAbsoluteGameTime,
                        playDealSound = !dealSoundScheduled,
                        playFlipSound = seatIndex !in flipSoundSeatIndices,
                        endPose = poseFor(tileId, presentation.tilePoseStates),
                    )
                    dealSoundScheduled = true
                    flipSoundSeatIndices += seatIndex
                }
            }
            batchStart += batchSize
        }

        table.blockEntity.markDirty()
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
     * 清除指定 controller 周圍且 table UUID 相符的所有正式管理中麻將牌——跟
     * [FabricMahjongTileWallPresenter.clear] 效果相同（都是清這張桌子的全部管理中麻將牌，不分子
     * 系統），保留成獨立方法只是維持介面對稱，呼叫端仍可能只想觸發這條路徑的清除。角落物件不在這裡清除
     * （見 [TablePropPresenter]）。
     *
     * @param tableId 桌子 UUID。
     * @param tableLocation 桌子位置。
     * @return 移除的管理中牌 entity 數量。
     */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val tiles = findManagedTiles(world, tableId, tableLocation.toBlockPos())
        tiles.forEach(MahjongTileEntity::discard)
        return tiles.size
    }

    /**
     * 由版本無關 dimension ID 取得目前 server session 的世界。
     *
     * @param location 桌子位置。
     * @return 對應的已載入伺服器世界；無法解析時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /**
     * 只查詢桌子結構附近並以同步 UUID 精確篩選，避免掃描整個 dimension。
     *
     * @param world 要搜尋的伺服器世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @return 由該桌管理的牌 entity。
     */
    private fun findManagedTiles(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongTileEntity> = world.getEntitiesByClass(
        MahjongTileEntity::class.java,
        Box(controllerPos).expand(TILE_SEARCH_HORIZONTAL, TILE_SEARCH_VERTICAL, TILE_SEARCH_HORIZONTAL),
    ) { tile -> tile.managedTableId == tableId }

    /**
     * 將版本無關 table location 轉回 Fabric 方塊座標。
     *
     * @return 對應的 Fabric 方塊座標。
     */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式手牌／副露建立與查詢使用的固定參數。 */
    private companion object {

        /** controller 周圍查詢正式手牌／副露的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式手牌／副露的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0
    }

    /** 依逐張共同姿態轉成原生姿態；未指定的牌保持直立。 */
    private fun poseFor(tileId: Uuid, states: List<TilePoseState>): MahjongTilePose = when (
        states.firstOrNull { it.tileId == tileId }?.pose ?: TilePose.UPRIGHT
    ) {
        TilePose.UPRIGHT -> MahjongTilePose.STANDING
        TilePose.FACE_UP -> MahjongTilePose.FACE_UP
        TilePose.FACE_DOWN -> MahjongTilePose.FACE_DOWN
    }
}
