package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
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

/** 使用 Fabric 1.20.1 entity 呈現正式牌河：把牌牆管理中的既有 entity 領走並移動，不重新生成。 */
@Single(binds = [MahjongDiscardPresenter::class])
class FabricMahjongDiscardPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongDiscardPresenter {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 牌河裡的每一張牌，UUID 都跟牌牆結構座標傳過來的那張牌完全同一個——理由跟
     * [FabricMahjongHandTilesPresenter.present] 完全一致，這裡只用 `World.getEntity` 依 UUID 找到
     * 既有 entity，直接改標記、改姿態（[MahjongTilePose.FACE_UP]，牌河牌面永遠朝上可見）、移動到牌河
     * 位置——絕對不能另外 `spawnEntity`。
     *
     * [MahjongDiscardPresentation.discardTileIds] 的順序本身就是捨牌順序，直接依索引呼叫
     * [MahjongTileTableLayout.discardPlacement]；[MahjongDiscardPresentation.sidewaysMarkedTileId]
     * 相符的那張額外標記側身。找不到對應 UUID 的既有 entity 時該筆直接跳過並記警告 log，不中斷其餘
     * 牌的呈現，比照本介面 best-effort 的既有慣例。
     *
     * 這次呼叫不做「清除上一局遺留牌河」的整批清除——牌牆 presenter 每次重新生成牌牆時已經整批清空
     * 這張桌子所有管理中的麻將牌（見 [FabricMahjongTileWallPresenter.present] KDoc），牌牆一定比
     * 摸牌/丟牌先執行，上一局的舊 entity 早就不存在了。
     */
    override fun present(presentation: MahjongDiscardPresentation): MahjongDiscardPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongDiscardPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongDiscardPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongDiscardPresentationResult.TABLE_NOT_FOUND
        }

        val wallRemaining = sideStillHasWall(
            world,
            presentation.tableId,
            controllerPos,
            presentation.tableFacing,
            presentation.seatIndex,
        )

        val sidewaysMarkedDiscardIndex = presentation.sidewaysMarkedTileId
            ?.let { sidewaysTileId -> presentation.discardTileIds.indexOf(sidewaysTileId) }
            ?.takeIf { it >= 0 }

        var missingTileCount = 0
        presentation.discardTileIds.forEachIndexed { discardIndex, tileId ->
            val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity
            if (tile == null) {
                missingTileCount++
                logger.warn(
                    "publishDiscardPileUpdated tableId={} tileId={} skipped: no existing wall entity found to claim",
                    presentation.tableId,
                    tileId,
                )
                return@forEachIndexed
            }
            val placement = MahjongTileTableLayout.discardPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.seatIndex,
                discardIndex = discardIndex,
                isSidewaysMarked = tileId == presentation.sidewaysMarkedTileId,
                sidewaysMarkedDiscardIndex = sidewaysMarkedDiscardIndex,
                wallRemaining = wallRemaining,
            )
            tile.assignToTable(presentation.tableId)
            if (tileId == presentation.newlyDiscardedTileId) {
                // 起飛前維持牌現在的既有姿態（手牌位置本來就是 MahjongTilePose.STANDING、面向玩家），
                // 不要在這裡先改成 FACE_UP——姿態要等 scheduleDiscardTileAnimation 隱形傳送那一刻才
                // 切換，見該方法 KDoc。
                scheduleDiscardTileAnimation(tile, placement)
            } else {
                tile.tilePose = MahjongTilePose.FACE_UP
                tile.teleportExistingManagedTile(placement)
            }
        }
        table.markDirty()
        if (missingTileCount > 0) {
            logger.warn(
                "publishDiscardPileUpdated tableId={} presented with {} missing tile(s) out of {}",
                presentation.tableId,
                missingTileCount,
                presentation.discardTileIds.size,
            )
            return MahjongDiscardPresentationResult.SPAWN_FAILED
        }
        return MahjongDiscardPresentationResult.PRESENTED
    }

    /**
     * 排定捨牌動畫：一次連續可見的拋物線飛行，從手牌現在的實際位置直接飛到牌河位置，不像摸牌／發牌
     * 動畫那樣中途隱形傳送——理由見 [MahjongTileTableLayout.DISCARD_ARC_HEIGHT] KDoc。姿態
     * （[MahjongTilePose.STANDING] 轉 [MahjongTilePose.FACE_UP]）跟位移用同一段動畫連續內插，玩家全程
     * 看得見牌從手牌翻轉、飛到牌河的過程。
     *
     * 側身旋轉（立直宣告牌）不連續內插——[finalPlacement] 的 yaw 本身已經是
     * [MahjongTileTableLayout.discardPlacement] 依 `isSidewaysMarked` 算好的最終朝向，這裡直接在起飛
     * 那一刻就把 yaw 設成最終值，讓牌以最終朝向飛過去（等同一開始就轉正，不是飛行途中才轉），跟真的
     * 連續旋轉 yaw 比起來是簡化，但實作成本低很多，且側身牌只在立直宣告時出現，效果可接受。
     */
    private fun scheduleDiscardTileAnimation(tile: MahjongTileEntity, finalPlacement: MahjongTileWallPlacement) {
        val startX = tile.x
        val startY = tile.y
        val startZ = tile.z
        tile.enqueueAll(
            listOf(
                AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
                AnimationStep.Custom(MahjongTilePose.FACE_UP),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS,
                    arcHeight = MahjongTileTableLayout.DISCARD_ARC_HEIGHT,
                    startOffsetX = startX - finalPlacement.x,
                    startOffsetY = startY - finalPlacement.y,
                    startOffsetZ = startZ - finalPlacement.z,
                    startPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_UP.rotationDegrees,
                ),
            ),
        )
    }

    /**
     * 清除指定 controller 周圍且 table UUID 相符的所有正式管理中麻將牌——跟
     * [FabricMahjongTileWallPresenter.clear] 效果相同（都是清這張桌子的全部管理中麻將牌，不分子
     * 系統），保留成獨立方法只是維持介面對稱，呼叫端仍可能只想觸發牌河這條路徑的清除。
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

    /**
     * 判斷麻將桌面向 [seatIndex] 該側是否還有牌牆——純用世界上既有麻將牌 entity 的姿態與朝向判斷，
     * 不查詢座標範圍：
     *
     * 1. 找出這張桌子目前所有管理中的麻將牌 entity（[findManagedTiles]，涵蓋牌牆、王牌區、手牌、
     *    摸牌位、牌河，不分子系統）。
     * 2. 篩選出姿態為 [MahjongTilePose.FACE_DOWN] 的那些——手牌／摸牌位固定 `STANDING`
     *    （見 [FabricMahjongHandTilesPresenter]），牌河固定 `FACE_UP`（見 [present]），只有牌牆／
     *    王牌區固定 `FACE_DOWN`（見 [FabricMahjongTileWallPresenter.present]），因此姿態本身就足以
     *    排除手牌、副露、牌河，不需要另外比對 UUID 是否還在牌牆結構裡。
     * 3. 再篩選 yaw 跟 [seatIndex] 自己那面牆的 yaw 一致——四面牆都是 `FACE_DOWN`，需要 yaw 才能
     *    分辨是哪一面；`seatIndex` 自己那面牆的 yaw 直接用
     *    `wallPlacement(dealerSeatIndex = seatIndex, position.side = 0, ...)` 算，理由同舊版
     *    KDoc：`advance` 步數為 0 時直接等於 `seatIndexToTableSide(seatIndex)`，不需要另外知道莊家
     *    是誰。
     *
     * 王牌區的牌被移出開門位置（[FabricMahjongTileWallPresenter.moveDeadWallToOpenPosition]）時只改
     * 位置，姿態與 yaw 都不變，因此天然滿足「王牌區視同牌牆」——不需要額外標記或排除。
     */
    private fun sideStillHasWall(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
    ): Boolean {
        val expectedYaw = MahjongTileTableLayout.wallPlacement(
            controllerX = controllerPos.x,
            controllerY = controllerPos.y,
            controllerZ = controllerPos.z,
            tableFacing = tableFacing,
            dealerSeatIndex = seatIndex,
            stacksPerSide = ASSUMED_STACKS_PER_SIDE,
            position = TileWallPosition(side = 0, stack = 0, layer = 0),
        ).yaw
        return findManagedTiles(world, tableId, controllerPos).any { tile ->
            tile.tilePose == MahjongTilePose.FACE_DOWN && yawMatches(tile.yaw, expectedYaw)
        }
    }

    /** 容許浮點誤差比較兩個 yaw 是否代表同一個世界朝向，先各自正規化到 `[0, 360)` 再比較差距。 */
    private fun yawMatches(a: Float, b: Float): Boolean {
        val normalizedA = ((a % FULL_YAW_DEGREES) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES
        val normalizedB = ((b % FULL_YAW_DEGREES) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES
        return kotlin.math.abs(normalizedA - normalizedB) < YAW_TOLERANCE_DEGREES
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

    /** 正式牌河建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式牌河的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式牌河的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0

        /** [sideStillHasWall] 假設的標準日麻每面墩數，理由同 [MahjongTileTableLayout] 內部同名假設。 */
        const val ASSUMED_STACKS_PER_SIDE: Int = 17

        /** [yawMatches] 比較兩個 yaw 時容許的浮點誤差（度）。 */
        const val YAW_TOLERANCE_DEGREES: Float = 0.01f

        /** [yawMatches] 正規化 yaw 用的完整一圈角度。 */
        const val FULL_YAW_DEGREES: Float = 360.0f
    }
}
