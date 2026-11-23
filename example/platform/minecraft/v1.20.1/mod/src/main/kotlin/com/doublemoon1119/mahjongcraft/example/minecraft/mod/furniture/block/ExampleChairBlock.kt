package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.block

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.seating.ExampleChairSeatService
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 可直接右鍵坐下的範例方塊椅子；與原版可含水方塊相同，放在水源中會含水。
 *
 * @param settings Minecraft 方塊設定。
 */
class ExampleChairBlock(
    settings: Properties,
) : Block(settings),
    SimpleWaterloggedBlock {
    /** 預設不含水。 */
    init {
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, false))
    }

    /** 椅子上半部的簡單碰撞形狀。 */
    private val shape: VoxelShape = box(1.0, 0.0, 1.0, 15.0, 9.0, 15.0)

    /** 椅子右鍵服務由範例 provider 共用。 */
    private val seatService = ExampleChairSeatService()

    /** 讓玩家右鍵椅子時嘗試坐下。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun use(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        if (player.isShiftKeyDown || player.isPassenger || player.getItemInHand(hand).`is`(ExampleActors.item)) return InteractionResult.PASS
        if (world is ServerLevel) seatService.sit(world, pos, player, player.yRot)
        return InteractionResult.sidedSuccess(world.isClientSide)
    }

    /** 提供椅子的簡單碰撞形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = shape

    /** 提供椅子的選取形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = shape

    /** 放在水源中時含水。 */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, context.level.getFluidState(context.clickedPos).type == Fluids.WATER)

    /** 含水時回報水源的流體狀態。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = if (state.getValue(BlockStateProperties.WATERLOGGED)) Fluids.WATER.getSource(false) else Fluids.EMPTY.defaultFluidState()

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
        if (state.getValue(BlockStateProperties.WATERLOGGED)) world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    /** 宣告含水狀態。 */
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.WATERLOGGED)
    }
}
