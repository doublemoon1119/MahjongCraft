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
    /**
     * 將桌上兩種 overlay 隱藏至指定遊戲時間。
     *
     * @param world 桌子所在世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @param gameTime 隱藏 lease 的結束遊戲時間。
     */
    fun hideUntil(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos, gameTime: Long) {
        findRoundInfo(world, tableId, controllerPos).forEach { it.hideUntil(gameTime) }
        findPlayerInfo(world, tableId, controllerPos).forEach { it.hideUntil(gameTime) }
    }

    /**
     * 將桌上兩種 overlay 持續隱藏至 entity 移除。
     *
     * @param world 桌子所在世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     */
    fun hideUntilRemoved(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos) {
        findRoundInfo(world, tableId, controllerPos).forEach(MahjongRoundInfoEntity::hideUntilRemoved)
        findPlayerInfo(world, tableId, controllerPos).forEach(MahjongPlayerInfoEntity::hideUntilRemoved)
    }

    /**
     * 新局 presentation 建立後，解除桌上兩種 overlay 的隱藏 lease 並立即顯示，
     * 避免沿用上一局留下的無期限隱藏狀態。
     *
     * @param world 桌子所在世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     */
    fun showNow(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos) {
        findRoundInfo(world, tableId, controllerPos).forEach(MahjongRoundInfoEntity::showNow)
        findPlayerInfo(world, tableId, controllerPos).forEach(MahjongPlayerInfoEntity::showNow)
    }

    /**
     * 移除桌上兩種 overlay，並回傳移除數量。
     *
     * @param world 桌子所在世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @return 移除的 entity 數量。
     */
    fun clear(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): Int {
        val entities = findRoundInfo(world, tableId, controllerPos) + findPlayerInfo(world, tableId, controllerPos)
        entities.forEach { it.discard() }
        return entities.size
    }

    /**
     * 建立常駐 overlay 的鄰近 entity 搜尋範圍。
     *
     * @param pos 桌子 controller 方塊位置。
     * @return 搜尋範圍。
     */
    private fun searchBox(pos: BlockPos) = Box(pos).expand(
        TableOverlayEntitySearch.HORIZONTAL_RADIUS,
        TableOverlayEntitySearch.VERTICAL_RADIUS,
        TableOverlayEntitySearch.HORIZONTAL_RADIUS,
    )

    /**
     * 搜尋桌子附近由該桌管理的回合資訊 overlay。
     *
     * @param world 桌子所在的已載入伺服器世界。
     * @param tableId 要比對的桌子 UUID。
     * @param pos 桌子 controller 方塊位置。
     * @return 搜尋範圍內由該桌管理的回合資訊 entity。
     */
    private fun findRoundInfo(world: ServerWorld, tableId: Uuid, pos: BlockPos) = world.getEntitiesByClass(MahjongRoundInfoEntity::class.java, searchBox(pos)) { it.managedTableId == tableId }

    /**
     * 搜尋桌子附近由該桌管理的玩家資訊 overlay。
     *
     * @param world 桌子所在的已載入伺服器世界。
     * @param tableId 要比對的桌子 UUID。
     * @param pos 桌子 controller 方塊位置。
     * @return 搜尋範圍內由該桌管理的玩家資訊 entity。
     */
    private fun findPlayerInfo(world: ServerWorld, tableId: Uuid, pos: BlockPos) = world.getEntitiesByClass(MahjongPlayerInfoEntity::class.java, searchBox(pos)) { it.managedTableId == tableId }
}
