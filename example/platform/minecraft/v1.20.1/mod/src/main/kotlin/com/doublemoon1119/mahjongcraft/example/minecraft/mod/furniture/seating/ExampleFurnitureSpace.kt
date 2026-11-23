package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.seating

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.entity.ExampleCushionEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB

/** 家具放置與乘坐共用的已載入空間查詢，不為碰撞檢查載入區塊。 */
internal object ExampleFurnitureSpace {
    /**
     * 檢查範圍位於世界邊界與建造高度內，且相鄰碰撞查詢所需的區塊皆已載入。
     *
     * @param world 目前的伺服器世界。
     * @param box 即將使用的身體或家具範圍。
     * @return 可安全執行碰撞查詢時為 true。
     */
    fun isLoaded(world: ServerLevel, box: AABB): Boolean {
        if (box.minY < world.minBuildHeight || box.maxY >= world.maxBuildHeight || !world.worldBorder.isWithinBounds(box)) return false
        val minimum = BlockPos.containing(box.minX - 1.0, box.minY, box.minZ - 1.0)
        val maximum = BlockPos.containing(box.maxX + 1.0, box.maxY, box.maxZ + 1.0)
        for (chunkX in (minimum.x shr 4)..(maximum.x shr 4)) {
            for (chunkZ in (minimum.z shr 4)..(maximum.z shr 4)) {
                if (!world.chunkSource.hasChunk(chunkX, chunkZ)) return false
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
    fun isClear(world: ServerLevel, actor: Entity, box: AABB, ignoredSeat: Entity? = null): Boolean = isLoaded(world, box) &&
        world.noCollision(actor, box) &&
        world.getEntities(actor, box) {
            it !== ignoredSeat &&
                !it.isRemoved &&
                !it.isSpectator &&
                (it is LivingEntity || it is ExampleCushionEntity || it.canBeCollidedWith())
        }.isEmpty()
}
