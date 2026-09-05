package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 統一流局結算舞台的生成與 round-info visibility lease 交接。 */
@Single
class FabricExhaustiveDrawSettlementPresentationScheduler(
    private val overlays: PersistentTableOverlayCoordinator,
) {
    /** 成功生成時回傳固定結束時間；失敗則不隱藏 round info。 */
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
        if (!world.spawnEntity(stage)) return null
        val endGameTime = startGameTime + ExhaustiveDrawSettlementPresentationEntity.durationTicks(playerSnapshots)
        request.players.forEach { player ->
            val handTileIds = player.handTileIds.distinct()
            val cornerYieldShift = MahjongTileTableLayout.handCornerYieldShift(
                handSize = handTileIds.size,
                reservedCornerWidth = reservedCornerWidthsBySeat[player.ranking.seatIndex] ?: 0.0,
            )
            handTileIds.forEachIndexed { orderIndex, tileId ->
                val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity ?: return@forEachIndexed
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
                when (player.handPresentation) {
                    ExhaustiveDrawSettlementHandPresentation.REVEAL_TENPAI,
                    ExhaustiveDrawSettlementHandPresentation.REVEAL_PROOF,
                    -> {
                        revealedTileAssetsById[tileId]?.let { asset ->
                            tile.revealForPresentation(asset, endGameTime + PRESENTATION_REVEAL_GRACE_TICKS)
                        }
                        TileAnimationSteps.scheduleLaydown(
                            tile,
                            startGameTime + HAND_LAYDOWN_START_TICK,
                            playGroupSound = orderIndex == handTileIds.size / 2,
                        )
                    }

                    ExhaustiveDrawSettlementHandPresentation.CONCEAL -> {
                        TileAnimationSteps.scheduleConceal(
                            tile,
                            startGameTime + HAND_LAYDOWN_START_TICK,
                            playGroupSound = orderIndex == handTileIds.size / 2,
                        )
                    }
                }
            }
        }
        overlays.hideUntilRemoved(world, tableId, controllerPos)
        return endGameTime
    }

    private companion object {
        const val HAND_LAYDOWN_START_TICK = 30L
        const val HAND_REORDER_START_TICK = 5L
        const val STAGE_HEIGHT_OFFSET = 1.4

        /** 讓牌面公開 lease 涵蓋面板到期與下一個 server tick 的清場交接。 */
        const val PRESENTATION_REVEAL_GRACE_TICKS = 20L
    }
}
