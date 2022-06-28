package doublemoon.mahjongcraft.block

import doublemoon.mahjongcraft.entity.SeatEntity
import doublemoon.mahjongcraft.util.boxBySize
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World

class MahjongStool(settings: Settings) : Block(settings) {

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult
    ): ActionResult =
        if (hand === Hand.MAIN_HAND && !world.isClient && SeatEntity.canSpawnAt(
                world = world as ServerWorld,
                pos = pos
            )
        ) {
            SeatEntity.spawnAt(world = world, pos = pos, entity = player, sitOffsetY = 0.4)
            ActionResult.SUCCESS
        } else {
            ActionResult.PASS
        }

    override fun getPistonBehavior(state: BlockState): PistonBehavior = PistonBehavior.DESTROY

    override fun getOutlineShape(
        state: BlockState?,
        world: BlockView?,
        pos: BlockPos?,
        context: ShapeContext?
    ): VoxelShape = SHAPE

    companion object {
        private val SHAPE: VoxelShape

        init {
            val bottom1: VoxelShape = boxBySize(3, 4, 5, 2, 2, 6)
            val bottom2: VoxelShape = boxBySize(11, 4, 5, 2, 2, 6)
            val bottom3: VoxelShape = boxBySize(5, 4, 3, 6, 2, 2)
            val bottom4: VoxelShape = boxBySize(5, 4, 11, 6, 2, 2)
            val pillar1: VoxelShape = boxBySize(3, 0, 3, 2, 8, 2)
            val pillar2: VoxelShape = boxBySize(11, 0, 11, 2, 8, 2)
            val pillar3: VoxelShape = boxBySize(3, 0, 11, 2, 8, 2)
            val pillar4: VoxelShape = boxBySize(11, 0, 3, 2, 8, 2)
            val top: VoxelShape = boxBySize(1.0, 8.0, 1.0, 14.0, 2.0, 14.0)
            SHAPE = VoxelShapes.union(bottom1, bottom2, bottom3, bottom4, pillar1, pillar2, pillar3, pillar4, top)
        }
    }
}