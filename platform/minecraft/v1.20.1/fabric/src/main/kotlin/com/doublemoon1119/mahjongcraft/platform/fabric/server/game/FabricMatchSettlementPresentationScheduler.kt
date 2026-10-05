package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 終局排行舞台的生成與 round-info visibility lease 交接。 */
@Single
class FabricMatchSettlementPresentationScheduler(
    private val templateRegistry: MatchSettlementPresentationTemplateRegistry,
    private val overlays: PersistentTableOverlayCoordinator,
    private val spawnGateway: FabricEntitySpawnGateway,
) {
    private val logger = mahjongCraftLogger(FabricMatchSettlementPresentationScheduler::class)

    private val warnedRuleModuleIds = mutableSetOf<String>()

    /** 成功生成時回傳固定結束時間；失敗則不隱藏 round info。 */
    fun schedule(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        placement: MahjongTileWallPlacement,
        earliestStartGameTime: Long,
        request: MatchSettlementPresentationRequest,
    ): Long? {
        val template = templateRegistry.findForRule(request.ruleModuleId) ?: run {
            if (warnedRuleModuleIds.add(request.ruleModuleId)) {
                logger.warn("No match settlement template for rule module: {}", request.ruleModuleId)
            }
            return null
        }
        val startGameTime = maxOf(world.time, earliestStartGameTime)
        val snapshots = request.players.map { player ->
            MatchSettlementPlayerSnapshot(
                playerId = player.playerId.toString(),
                seatIndex = player.seatIndex,
                isAi = player.isAi,
                initialSeatIndex = player.initialSeatIndex,
                finalScore = player.finalScore,
                finalRank = player.finalRank,
            )
        }
        val stage = MatchSettlementPresentationEntity(world = world).apply {
            configure(tableId, startGameTime, snapshots, template)
            refreshPositionAndAngles(placement.x, placement.y + STAGE_HEIGHT_OFFSET, placement.z, placement.yaw, 0f)
        }
        if (!spawnGateway.spawn(world, stage, "match-settlement", tableId)) return null
        val endGameTime = stage.endGameTime
        overlays.hideUntilRemoved(world, tableId, controllerPos)
        return endGameTime
    }

    private companion object {
        const val STAGE_HEIGHT_OFFSET = 1.6
    }
}
