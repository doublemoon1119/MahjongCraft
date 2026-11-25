package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.MahjongStoolSeatService
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 使用 [design] 固定款式、水平朝向及其碰撞幾何的單方塊麻將凳。
 *
 * 右鍵即可坐下，不論手上拿著什麼；蹲下或正在騎乘時不坐下。凳子本身不保存牌局或座位狀態。
 * 與原版可含水方塊相同，放在水源中會含水。
 *
 * @param settings Minecraft 方塊設定。
 * @property design 此方塊 registry ID 對應的固定凳子款式。
 * @property seatService 讓玩家坐上凳子的伺服器端入口。
 */
class MahjongStoolBlock(
    settings: Properties,
    val design: MahjongStoolDesign,
    private val seatService: MahjongStoolSeatService,
) : MahjongBlock(settings),
    SimpleWaterloggedBlock {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false),
        )
    }

    /** 放置時讓凳子的正面開口朝向玩家，放在水源中時含水。 */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = defaultBlockState()
        .setValue(BlockStateProperties.HORIZONTAL_FACING, context.horizontalDirection.opposite)
        .waterloggedAt(context.level, context.clickedPos)

    /** 含水時回報水源的流體狀態。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = state.waterloggedFluidState()

    /** 含水時排程水流更新，讓周圍的水照常流動。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos,
    ): BlockState {
        state.scheduleWaterTickIfWaterlogged(world, pos)
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    /**
     * 讓右鍵的玩家坐上凳子。
     *
     * 蹲下或正在騎乘時交回原版處理；其他情況一律消耗這次右鍵，即使凳子已被占用而沒有坐下，手上的物品也不會被使用。
     */
    override fun useWithoutItem(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult {
        if (player.isShiftKeyDown || player.isPassenger) return InteractionResult.PASS
        if (world is ServerLevel) seatService.sit(world, pos, player)
        return InteractionResult.sidedSuccess(world.isClientSide)
    }

    /** 回傳依凳子方向旋轉的碰撞形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = design.shapeFor(state.getValue(BlockStateProperties.HORIZONTAL_FACING))

    /** 回傳與碰撞相同並依凳子方向旋轉的選取形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = design.shapeFor(state.getValue(BlockStateProperties.HORIZONTAL_FACING))

    /** 套用結構方塊等操作要求的水平旋轉。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(
        BlockStateProperties.HORIZONTAL_FACING,
        rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)),
    )

    /** 套用結構方塊等操作要求的水平鏡像。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun mirror(state: BlockState, mirror: Mirror): BlockState = state.rotate(
        mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)),
    )

    /** 宣告所有麻將凳皆保存水平朝向與含水狀態。 */
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.WATERLOGGED)
    }
}
