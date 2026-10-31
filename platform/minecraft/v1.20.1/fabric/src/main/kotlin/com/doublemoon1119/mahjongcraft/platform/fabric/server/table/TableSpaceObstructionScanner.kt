package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTableStructureBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.playSpaceCells
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSpace
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceObstruction
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

/** 讀取世界中擺牌層的方塊，找出擋住擺牌的格子。 */
internal object TableSpaceObstructionScanner {
    /** 方塊座標換算 chunk 座標的位移量。 */
    private const val CHUNK_SHIFT: Int = 4

    /**
     * 找出指定桌子擺牌層中擋住擺牌的格子。
     *
     * @param world 桌子所在世界。
     * @param controllerPos 控制方塊位置。
     * @return 擋住的格子，依 [MahjongTableSpace.playSpaceHorizontalOffsets] 的順序；擺牌層有區塊未載入時為 null。
     */
    fun obstructedCells(
        world: ServerWorld,
        controllerPos: BlockPos,
    ): List<BlockPos>? {
        val cells = MahjongTableSpace.playSpaceCells(controllerPos)
        if (cells.any { !world.chunkManager.isChunkLoaded(it.x shr CHUNK_SHIFT, it.z shr CHUNK_SHIFT) }) return null
        val controller = controllerPos.toImmutable()
        return cells.filter { cell ->
            val state = world.getBlockState(cell)
            val owner = (state.block as? MahjongTableStructureBlock)?.mahjongTableControllerPos(world, cell, state)
            TableSpaceObstruction.isObstructing(
                isAir = state.isAir,
                hasFluid = !state.fluidState.isEmpty,
                owningController = owner?.toImmutable(),
                controller = controller,
            )
        }
    }
}
