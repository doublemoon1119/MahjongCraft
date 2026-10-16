package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawHandDisclosure
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 統一流局結算舞台的生成與 round-info visibility lease 交接。
 *
 * @property overlays 管理桌面 overlay 的暫時隱藏。
 * @property spawnGateway 生成並追蹤舞台 entity。
 */
class FabricExhaustiveDrawSettlementPresentationScheduler(
    private val overlays: PersistentTableOverlayCoordinator,
    private val spawnGateway: FabricEntitySpawnGateway,
) {
    /** 記錄手牌 entity 找不到的 logger。 */
    private val logger = mahjongCraftLogger(FabricExhaustiveDrawSettlementPresentationScheduler::class)

    /**
     * 成功生成時回傳固定結束時間；失敗則不隱藏 round info。手牌 entity 找不到的牌不播放動畫，並記錄一筆警告列出各座位缺少的牌。
     *
     * @param world 生成舞台與查找牌 entity 的世界。
     * @param tableId 對局所在牌桌識別碼。
     * @param controllerPos 牌桌 controller 方塊位置。
     * @param tableFacing 牌桌朝向。
     * @param placement 舞台的世界位置與朝向。
     * @param request 流局結算顯示資料。
     * @param waitingTileAssetsBySeat 各座位聽牌等待牌的素材 key。
     * @param revealedTileAssetsById 可公開牌張識別碼到素材 key 的對應。
     * @param reservedCornerWidthsBySeat 各座位為其他牌面預留的桌角寬度。
     * @return 舞台固定結束的絕對 game time；未能生成時為 null。
     */
    fun schedule(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        tableFacing: MahjongTableFacing,
        placement: MahjongTileWallPlacement,
        request: ExhaustiveDrawSettlementPresentationRequest,
        waitingTileAssetsBySeat: Map<Int, List<String>>,
        revealedTileAssetsById: Map<Uuid, String>,
        reservedCornerWidthsBySeat: Map<Int, Double>,
    ): Long? {
        val startGameTime = world.time
        val playerSnapshots = request.players.map { player ->
            val ranking = player.ranking
            ExhaustiveDrawSettlementPlayerSnapshot(
                playerId = ranking.playerId.toString(),
                seatIndex = ranking.seatIndex,
                wind = player.seatWind.name,
                isAi = ranking.isAi,
                previousScore = ranking.previousScore,
                currentScore = ranking.currentScore,
                previousRank = ranking.previousRank,
                currentRank = ranking.currentRank,
                statusId = player.statusId,
                revealedHandTileIds = player.revealedHandTileIds.map(Uuid::toString),
                revealedHandAssetKeys = player.revealedHandTileIds.mapNotNull(revealedTileAssetsById::get),
                waitingTileAssetKeys = waitingTileAssetsBySeat[ranking.seatIndex].orEmpty(),
            )
        }
        val stage = ExhaustiveDrawSettlementPresentationEntity(world = world).apply {
            configure(
                tableId = tableId,
                startGameTime = startGameTime,
                reasonId = request.reasonId,
                players = playerSnapshots,
            )
            refreshPositionAndAngles(placement.x, placement.y + STAGE_HEIGHT_OFFSET, placement.z, placement.yaw, 0f)
        }
        if (!spawnGateway.spawn(world, stage, "exhaustive-draw-settlement", tableId)) return null
        val endGameTime = startGameTime + ExhaustiveDrawSettlementPresentationEntity.durationTicks(playerSnapshots)
        val missingBySeat = mutableMapOf<Int, MutableList<Uuid>>()
        request.players.forEach { player ->
            val handTileIds = player.handTileIds.distinct()
            val cornerYieldShift = MahjongTileTableLayout.handCornerYieldShift(
                handSize = handTileIds.size,
                reservedCornerWidth = reservedCornerWidthsBySeat[player.ranking.seatIndex] ?: 0.0,
            )
            handTileIds.forEachIndexed { orderIndex, tileId ->
                val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity
                if (tile == null) {
                    missingBySeat.getOrPut(player.ranking.seatIndex, ::mutableListOf) += tileId
                    return@forEachIndexed
                }
                val sortedPlacement = MahjongTileTableLayout.handPlacement(
                    controllerX = controllerPos.x,
                    controllerY = controllerPos.y,
                    controllerZ = controllerPos.z,
                    tableFacing = tableFacing,
                    seatIndex = player.ranking.seatIndex,
                    handSize = handTileIds.size,
                    tileIndex = handTileIds.size - 1 - orderIndex,
                    cornerYieldShift = cornerYieldShift,
                )
                TileAnimationSteps.scheduleReorder(tile, sortedPlacement, startGameTime + HAND_REORDER_START_TICK)
                when (player.handDisclosure) {
                    ExhaustiveDrawHandDisclosure.TENPAI,
                    ExhaustiveDrawHandDisclosure.PROOF,
                    -> {
                        TileAnimationSteps.scheduleLaydown(
                            tile,
                            startGameTime + HAND_LAYDOWN_START_TICK,
                            playGroupSound = orderIndex == handTileIds.size / 2,
                        )
                    }

                    ExhaustiveDrawHandDisclosure.CONCEALED -> {
                        TileAnimationSteps.scheduleConceal(
                            tile,
                            startGameTime + HAND_LAYDOWN_START_TICK,
                            playGroupSound = orderIndex == handTileIds.size / 2,
                        )
                    }
                }
            }
        }
        missingPresentationTilesText(missingBySeat)?.let { missing ->
            logger.warn("Exhaustive draw settlement on table {} skips hand tiles whose entities are missing ({})", tableId, missing)
        }
        overlays.hideUntilRemoved(world, tableId, controllerPos)
        return endGameTime
    }

    private companion object {
        const val HAND_LAYDOWN_START_TICK = 30L
        const val HAND_REORDER_START_TICK = 5L
        const val STAGE_HEIGHT_OFFSET = 1.4
    }
}
