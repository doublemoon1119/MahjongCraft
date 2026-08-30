package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 同時管理 Round Info 與 Player Info 的常駐桌級 overlay lease 與清除。 */
@Single
class PersistentTableOverlayCoordinator {
    fun hideUntil(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos, gameTime: Long) {
        findRoundInfo(world, tableId, controllerPos).forEach { it.hideUntil(gameTime) }
        findPlayerInfo(world, tableId, controllerPos).forEach { it.hideUntil(gameTime) }
    }

    fun hideUntilRemoved(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos) {
        findRoundInfo(world, tableId, controllerPos).forEach(MahjongRoundInfoEntity::hideUntilRemoved)
        findPlayerInfo(world, tableId, controllerPos).forEach(MahjongPlayerInfoEntity::hideUntilRemoved)
    }

    /** 新局 presentation 已建立後，明確解除上一局留下的無期限隱藏 lease。 */
    fun showNow(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos) {
        findRoundInfo(world, tableId, controllerPos).forEach(MahjongRoundInfoEntity::showNow)
        findPlayerInfo(world, tableId, controllerPos).forEach(MahjongPlayerInfoEntity::showNow)
    }

    fun clear(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): Int {
        val entities = findRoundInfo(world, tableId, controllerPos) + findPlayerInfo(world, tableId, controllerPos)
        entities.forEach { it.discard() }
        return entities.size
    }

    private fun searchBox(pos: BlockPos) = Box(pos).expand(
        TableOverlayEntitySearch.HORIZONTAL_RADIUS,
        TableOverlayEntitySearch.VERTICAL_RADIUS,
        TableOverlayEntitySearch.HORIZONTAL_RADIUS,
    )

    private fun findRoundInfo(world: ServerWorld, tableId: Uuid, pos: BlockPos) = world.getEntitiesByClass(MahjongRoundInfoEntity::class.java, searchBox(pos)) { it.managedTableId == tableId }

    private fun findPlayerInfo(world: ServerWorld, tableId: Uuid, pos: BlockPos) = world.getEntitiesByClass(MahjongPlayerInfoEntity::class.java, searchBox(pos)) { it.managedTableId == tableId }
}
