package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/** 家具放置與乘坐共用的已載入空間查詢，不為碰撞檢查載入區塊。 */
internal object ExampleFurnitureSpace {
    /**
     * 檢查範圍位於世界邊界與建造高度內，且相鄰碰撞查詢所需的區塊皆已載入。
     *
     * @param world 目前的伺服器世界。
     * @param box 即將使用的身體或家具範圍。
     * @return 可安全執行碰撞查詢時為 true。
     */
    fun isLoaded(world: ServerWorld, box: Box): Boolean {
        if (box.minY < world.bottomY || box.maxY >= world.topY || !world.worldBorder.contains(box)) return false
        val minimum = BlockPos.ofFloored(box.minX - 1.0, box.minY, box.minZ - 1.0)
        val maximum = BlockPos.ofFloored(box.maxX + 1.0, box.maxY, box.maxZ + 1.0)
        for (chunkX in (minimum.x shr 4)..(maximum.x shr 4)) {
            for (chunkZ in (minimum.z shr 4)..(maximum.z shr 4)) {
                if (!world.chunkManager.isChunkLoaded(chunkX, chunkZ)) return false
            }
        }
        return true
    }

    /**
     * 檢查方塊、其他乘坐者與家具是否占用指定範圍。
     *
     * @param world 目前的伺服器世界。
     * @param actor 即將入座或放置的實體，不當成阻擋者。
     * @param box 要檢查的範圍。
     * @param ignoredSeat 本次使用的坐墊，不當成自己的阻擋者。
     * @return 空間完整可用時為 true。
     */
    fun isClear(world: ServerWorld, actor: Entity, box: Box, ignoredSeat: Entity? = null): Boolean = isLoaded(world, box) &&
        world.isSpaceEmpty(actor, box) &&
        world.getOtherEntities(actor, box) {
            it !== ignoredSeat &&
                !it.isRemoved &&
                !it.isSpectator &&
                (it is LivingEntity || it is ExampleCushionEntity || it.isCollidable)
        }.isEmpty()
}
