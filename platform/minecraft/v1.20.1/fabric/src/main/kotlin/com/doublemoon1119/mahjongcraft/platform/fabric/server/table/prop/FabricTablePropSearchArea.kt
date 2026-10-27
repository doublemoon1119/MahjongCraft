package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkPos

/** 原生規則物件的共同搜尋範圍與實體載入條件，不強制載入區塊。 */
internal object FabricTablePropSearchArea {
    /** 桌子基準方塊外的水平搜尋距離。 */
    private const val HORIZONTAL_RADIUS = 2.0

    /** 桌子基準方塊外的垂直搜尋距離。 */
    private const val VERTICAL_RADIUS = 2.0

    /**
     * 建立包含桌子附近規則物件的搜尋盒。
     *
     * @param pos 桌子的基準方塊位置。
     * @return 與載入條件檢查使用相同邊界的搜尋盒。
     */
    fun box(pos: BlockPos): Box = Box(pos).expand(HORIZONTAL_RADIUS, VERTICAL_RADIUS, HORIZONTAL_RADIUS)

    /**
     * 確認落點在可重新找回的範圍內，拒絕極端偏移及非有限座標。
     *
     * @param pos 桌子的基準位置。
     * @param x 物件落點的世界 X 座標。
     * @param y 物件底部的世界 Y 座標。
     * @param z 物件落點的世界 Z 座標。
     * @return 落點嚴格位於搜尋盒內部時為 true。
     */
    fun contains(
        pos: BlockPos,
        x: Double,
        y: Double,
        z: Double,
    ): Boolean {
        val bounds = box(pos)
        return x > bounds.minX && x < bounds.maxX && y > bounds.minY && y < bounds.maxY && z > bounds.minZ && z < bounds.maxZ
    }

    /**
     * 確認搜尋範圍的方塊與保存實體都已載入，避免把尚未讀回的物件當成缺少而重生。
     *
     * @param world 桌子所在世界。
     * @param pos 桌子的基準方塊位置。
     * @return 搜尋範圍的所有區塊都已能查詢保存實體時為 true。
     */
    fun entitiesLoaded(world: ServerWorld, pos: BlockPos): Boolean = entitiesLoaded(
        pos = pos,
        blocksLoaded = { x, z -> world.chunkManager.isChunkLoaded(x, z) },
        savedEntitiesLoaded = world::isChunkLoaded,
    )

    /**
     * 以查詢函式檢查每個搜尋區塊，區分方塊與保存實體的就緒狀態。
     *
     * @param pos 桌子的基準位置。
     * @param blocksLoaded 檢查方塊區塊是否已載入。
     * @param savedEntitiesLoaded 檢查保存實體是否已載入。
     * @return 兩項查詢對每個搜尋區塊都通過時為 true。
     */
    internal fun entitiesLoaded(
        pos: BlockPos,
        blocksLoaded: (Int, Int) -> Boolean,
        savedEntitiesLoaded: (Long) -> Boolean,
    ): Boolean {
        val bounds = box(pos)
        val min = BlockPos.ofFloored(bounds.minX, bounds.minY, bounds.minZ)
        val max = BlockPos.ofFloored(bounds.maxX, bounds.maxY, bounds.maxZ)
        return ((min.x shr 4)..(max.x shr 4)).all { x ->
            ((min.z shr 4)..(max.z shr 4)).all { z -> blocksLoaded(x, z) && savedEntitiesLoaded(ChunkPos.toLong(x, z)) }
        }
    }
}
