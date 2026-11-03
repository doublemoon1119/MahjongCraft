package com.doublemoon1119.mahjongcraft.example.fabric.furniture.block

import com.doublemoon1119.mahjongcraft.example.fabric.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleChairSeatService
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.block.Waterloggable
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World
import net.minecraft.world.WorldAccess

/**
 * 可直接右鍵坐下的範例方塊椅子；與原版可含水方塊相同，放在水源中會含水。
 *
 * @param settings Minecraft 方塊設定。
 */
class ExampleChairBlock(
    settings: Settings,
) : Block(settings),
    Waterloggable {
    /** 預設不含水。 */
    init {
        defaultState = stateManager.defaultState.with(Properties.WATERLOGGED, false)
    }

    /** 椅子上半部的簡單碰撞形狀。 */
    private val shape: VoxelShape = createCuboidShape(1.0, 0.0, 1.0, 15.0, 9.0, 15.0)

    /** 椅子右鍵服務由範例 provider 共用。 */
    private val seatService = ExampleChairSeatService()

    /** 讓玩家右鍵椅子時嘗試坐下。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult,
    ): ActionResult {
        if (player.isSneaking || player.hasVehicle() || player.getStackInHand(hand).isOf(ExampleActors.item)) return ActionResult.PASS
        if (world is ServerWorld) seatService.sit(world, pos, player, player.yaw)
        return ActionResult.success(world.isClient)
    }

    /** 提供椅子的簡單碰撞形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = shape

    /** 提供椅子的選取形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = shape

    /** 放在水源中時含水。 */
    override fun getPlacementState(context: ItemPlacementContext): BlockState = defaultState.with(Properties.WATERLOGGED, context.world.getFluidState(context.blockPos).fluid == Fluids.WATER)

    /** 含水時回報水源的流體狀態。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = if (state.get(Properties.WATERLOGGED)) Fluids.WATER.getStill(false) else Fluids.EMPTY.defaultState

    /** 含水時排程水流更新，讓周圍的水照常流動。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getStateForNeighborUpdate(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: WorldAccess,
        pos: BlockPos,
        neighborPos: BlockPos,
    ): BlockState {
        if (state.get(Properties.WATERLOGGED)) world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world))
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos)
    }

    /** 宣告含水狀態。 */
    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.WATERLOGGED)
    }
}
