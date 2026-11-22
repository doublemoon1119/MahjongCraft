package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableOverlayEntitySearch
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import kotlin.uuid.Uuid

/** 同時管理 Round Info 與 Player Info 的常駐桌級 overlay lease 與清除。 */
class PersistentTableOverlayCoordinator {
    /**
     * 將桌上兩種 overlay 隱藏至指定遊戲時間。
     *
     * @param world 桌子所在世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @param gameTime 隱藏 lease 的結束遊戲時間。
     */
    fun hideUntil(world: ServerLevel, tableId: Uuid, controllerPos: BlockPos, gameTime: Long) {
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
    fun hideUntilRemoved(world: ServerLevel, tableId: Uuid, controllerPos: BlockPos) {
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
    fun showNow(world: ServerLevel, tableId: Uuid, controllerPos: BlockPos) {
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
    fun clear(world: ServerLevel, tableId: Uuid, controllerPos: BlockPos): Int {
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
    private fun searchBox(pos: BlockPos) = AABB(pos).inflate(
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
    private fun findRoundInfo(world: ServerLevel, tableId: Uuid, pos: BlockPos) = world.getEntitiesOfClass(MahjongRoundInfoEntity::class.java, searchBox(pos)) { it.managedTableId == tableId }

    /**
     * 搜尋桌子附近由該桌管理的玩家資訊 overlay。
     *
     * @param world 桌子所在的已載入伺服器世界。
     * @param tableId 要比對的桌子 UUID。
     * @param pos 桌子 controller 方塊位置。
     * @return 搜尋範圍內由該桌管理的玩家資訊 entity。
     */
    private fun findPlayerInfo(world: ServerLevel, tableId: Uuid, pos: BlockPos) = world.getEntitiesOfClass(MahjongPlayerInfoEntity::class.java, searchBox(pos)) { it.managedTableId == tableId }
}
