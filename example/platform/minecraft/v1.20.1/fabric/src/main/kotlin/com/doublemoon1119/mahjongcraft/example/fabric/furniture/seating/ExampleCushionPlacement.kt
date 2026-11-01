package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureEntities
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.floor

/** 坐墊對齊格線、貼齊支撐表面與檢查支撐的共用規則。 */
internal object ExampleCushionPlacement {
    /** 判斷兩個表面相接時容許的浮點誤差。 */
    private const val SURFACE_EPSILON = 0.00001

    /**
     * 檢查支撐及占用後生成坐墊，成功才依玩家模式消耗物品。
     *
     * @param world 放置世界。
     * @param player 真正放置的玩家。
     * @param stack 當次手持物品。
     * @param position 坐墊底面位置。
     * @param yaw 放置朝向。
     * @param ignoredActor 本次將入座的角色，不當成坐墊的阻擋者。
     * @return 坐墊已成功生成時為 true。
     */
    fun place(
        world: ServerWorld,
        player: ServerPlayerEntity,
        stack: ItemStack,
        position: Vec3d,
        yaw: Float,
        ignoredActor: Entity? = null,
    ): Boolean {
        if (stack.isEmpty || !player.abilities.allowModifyWorld || player.isSpectator) return false
        val pos = BlockPos.ofFloored(position)
        if (!world.canPlayerModifyAt(player, pos) || !player.canModifyAt(world, pos)) return false
        val entity = ExampleCushionEntity(ExampleFurnitureEntities.cushion, world).apply {
            refreshPositionAndAngles(position.x, position.y, position.z, snapYaw(yaw), 0.0f)
        }
        if (hasSupport(world, entity.boundingBox) != true) return false
        if (!ExampleFurnitureSpace.isClear(world, ignoredActor ?: entity, entity.boundingBox)) return false
        if (!world.spawnEntity(entity)) return false
        if (!player.abilities.creativeMode) stack.decrement(1)
        return true
    }

    /**
     * 將坐墊放在命中方塊的水平中央，保留實際命中頂面的高度。
     *
     * @param blockPos 命中的支撐方塊。
     * @param surfaceY 命中頂面的世界 Y 座標，可為半格或其他非整數高度。
     * @return 坐墊底面的世界位置。
     */
    fun positionAt(blockPos: BlockPos, surfaceY: Double): Vec3d = Vec3d(blockPos.x + 0.5, surfaceY, blockPos.z + 0.5)

    /**
     * 將放置朝向對齊最近的四個水平正方向。
     *
     * @param yaw 玩家放置時的水平角度。
     * @return 0、90、180 或 270 度。
     */
    fun snapYaw(yaw: Float): Float = (((floor((yaw.toDouble() + 45.0) / 90.0).toInt() % 4) + 4) % 4) * 90.0f

    /**
     * 檢查坐墊底面是否與至少一塊支撐表面接觸，不接受只有側邊接觸。
     *
     * @param cushion 坐墊在世界中的包圍盒。
     * @param surfaces 支撐方塊形狀拆成的世界座標包圍盒。
     * @return 有水平重疊且高度相接的頂面時為 true。
     */
    fun hasSurfaceSupport(cushion: Box, surfaces: List<Box>): Boolean = surfaces.any { surface ->
        abs(surface.maxY - cushion.minY) <= SURFACE_EPSILON &&
            surface.maxX > cushion.minX + SURFACE_EPSILON &&
            surface.minX < cushion.maxX - SURFACE_EPSILON &&
            surface.maxZ > cushion.minZ + SURFACE_EPSILON &&
            surface.minZ < cushion.maxZ - SURFACE_EPSILON
    }

    /**
     * 查詢底面下方已載入方塊的外形，供放置與存續檢查使用。
     *
     * 不載入區塊；鄰接區塊尚未載入時不判定家具失去支撐。
     *
     * @param world 坐墊所在的伺服器世界。
     * @param cushion 坐墊包圍盒。
     * @return 是否有支撐；所需區塊未載入時為 null。
     */
    fun hasSupport(world: ServerWorld, cushion: Box): Boolean? {
        val minimum = BlockPos.ofFloored(cushion.minX, cushion.minY - SURFACE_EPSILON, cushion.minZ)
        val maximum = BlockPos.ofFloored(cushion.maxX - SURFACE_EPSILON, cushion.minY - SURFACE_EPSILON, cushion.maxZ - SURFACE_EPSILON)
        val surfaces = mutableListOf<Box>()
        for (x in minimum.x..maximum.x) {
            for (z in minimum.z..maximum.z) {
                if (!world.chunkManager.isChunkLoaded(x shr 4, z shr 4)) return null
                val pos = BlockPos(x, minimum.y, z)
                surfaces += world.getBlockState(pos).getOutlineShape(world, pos).boundingBoxes.map { it.offset(pos) }
            }
        }
        return hasSurfaceSupport(cushion, surfaces)
    }
}
