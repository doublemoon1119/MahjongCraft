package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.RoundSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.RoundSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 統一流局結算舞台的生成與 round-info visibility lease 交接。 */
@Single
class FabricRoundSettlementPresentationScheduler {
    /** 成功生成時回傳固定結束時間；失敗則不隱藏 round info。 */
    fun schedule(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        placement: MahjongTileWallPlacement,
        request: RoundSettlementPresentationRequest,
        waitingTileAssetsBySeat: Map<Int, List<String>>,
        revealedTileAssetsById: Map<Uuid, String>,
    ): Long? {
        val startGameTime = world.time
        val playerSnapshots = request.players.map { player ->
            RoundSettlementPlayerSnapshot(
                playerId = player.playerId.toString(),
                seatIndex = player.seatIndex,
                wind = player.currentWind.name,
                isAi = player.isAi,
                previousScore = player.previousScore,
                currentScore = player.currentScore,
                previousRank = player.previousRank,
                currentRank = player.currentRank,
                statusId = player.statusId,
                revealedHandTileIds = player.revealedHandTileIds.map(Uuid::toString),
                revealedHandAssetKeys = player.revealedHandTileIds.mapNotNull(revealedTileAssetsById::get),
                waitingTileAssetKeys = waitingTileAssetsBySeat[player.seatIndex].orEmpty(),
            )
        }
        val stage = RoundSettlementPresentationEntity(world = world).apply {
            configure(
                tableId = tableId,
                startGameTime = startGameTime,
                reasonId = request.reasonId,
                players = playerSnapshots,
            )
            refreshPositionAndAngles(placement.x, placement.y + STAGE_HEIGHT_OFFSET, placement.z, placement.yaw, 0f)
        }
        if (!world.spawnEntity(stage)) return null
        val endGameTime = startGameTime + RoundSettlementPresentationEntity.durationTicks(playerSnapshots)
        request.players.forEach { player ->
            player.handTileIds.distinct().forEach { tileId ->
                val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity ?: return@forEach
                when (player.handPresentation) {
                    RoundSettlementHandPresentation.REVEAL_TENPAI,
                    RoundSettlementHandPresentation.REVEAL_PROOF,
                    -> {
                        revealedTileAssetsById[tileId]?.let { asset -> tile.revealForPresentation(asset, endGameTime) }
                        TileAnimationSteps.scheduleLaydown(tile, startGameTime + HAND_LAYDOWN_START_TICK)
                    }

                    RoundSettlementHandPresentation.CONCEAL ->
                        TileAnimationSteps.scheduleConceal(tile, startGameTime + HAND_LAYDOWN_START_TICK)
                }
            }
        }
        world.getEntitiesByClass(MahjongRoundInfoEntity::class.java, Box(controllerPos).expand(2.0, 2.0, 2.0)) {
            it.managedTableId == tableId
        }.firstOrNull()?.hideUntil(endGameTime)
        return endGameTime
    }

    private companion object {
        const val HAND_LAYDOWN_START_TICK = 30L
        const val STAGE_HEIGHT_OFFSET = 1.4
    }
}
