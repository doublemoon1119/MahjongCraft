package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.TableOwnedPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 集中清除指定麻將桌擁有的暫時性舞台與仍在執行的演出工作。 */
@Single
class FabricTablePresentationCleaner(
    private val winCelebrationEffectScheduler: FabricWinCelebrationEffectScheduler,
) {
    /** 立即停止指定桌子的所有暫時性呈現，並回傳移除的世界實體數量。 */
    fun clear(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): Int {
        val presentations = world.getEntitiesByClass(
            Entity::class.java,
            Box(controllerPos).expand(SEARCH_RADIUS),
        ) { entity -> (entity as? TableOwnedPresentationEntity)?.managedTableId == tableId }
        presentations.forEach(Entity::discard)
        winCelebrationEffectScheduler.cancel(tableId)
        return presentations.size
    }

    private companion object {
        /** 涵蓋桌面舞台與正常遊戲演出位移範圍的搜尋半徑。 */
        const val SEARCH_RADIUS: Double = 32.0
    }
}
