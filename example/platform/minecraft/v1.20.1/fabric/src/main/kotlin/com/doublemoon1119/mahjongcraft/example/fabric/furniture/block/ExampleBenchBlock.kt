package com.doublemoon1119.mahjongcraft.example.fabric.furniture.block

import com.doublemoon1119.mahjongcraft.example.fabric.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
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
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World
import net.minecraft.world.WorldAccess

/**
 * 每格一席、沿左右延長的範例長凳，不建立整排控制器。
 *
 * @param settings 原生方塊設定。
 */
class ExampleBenchBlock(settings: Settings) :
    Block(settings),
    Waterloggable {
    /** 真人與角色共用的家具入座服務。 */
    private val seats = ExampleChairSeatService()

    init {
        defaultState = stateManager.defaultState
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.WATERLOGGED, false)
            .with(LEFT, false)
            .with(RIGHT, false)
    }

    /** 每格的椅面、靠背與端點扶手，共用模型相同的朝向。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = shape(state)

    /** 選取範圍與實際家具一致。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = shape(state)

    /** 開口面向放置者，座位物品能力另外換算所需放置視角。 */
    override fun getPlacementState(context: ItemPlacementContext): BlockState = connected(
        state = defaultState.with(Properties.HORIZONTAL_FACING, ExampleBenchPlacement.facing(context.horizontalPlayerFacing))
            .with(Properties.WATERLOGGED, context.world.getFluidState(context.blockPos).fluid == Fluids.WATER),
        world = context.world,
        pos = context.blockPos,
    )

    /** 排程固定範圍的鄰格檢查；原生排程會隨區塊保存及恢復。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBlockAdded(
        state: BlockState,
        world: World,
        pos: BlockPos,
        oldState: BlockState,
        notify: Boolean,
    ) {
        if (world is ServerWorld) {
            world.scheduleBlockTick(
                pos,
                this,
                CONNECTION_CHECK_TICKS,
            )
        }
    }

    /** 只重查本格左右兩格，使區塊邊界重新可用時恢復正確連接。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun scheduledTick(
        state: BlockState,
        world: ServerWorld,
        pos: BlockPos,
        random: Random,
    ) {
        val next = connected(
            state = state,
            world = world,
            pos = pos,
        )
        if (next != state) {
            world.setBlockState(
                pos,
                next,
                NOTIFY_ALL,
            )
        }
        world.scheduleBlockTick(
            pos,
            this,
            CONNECTION_CHECK_TICKS,
        )
    }

    /** 鄰格變動時立即重算局部連接，未載入的格子不查詢。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getStateForNeighborUpdate(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: WorldAccess,
        pos: BlockPos,
        neighborPos: BlockPos,
    ): BlockState {
        if (state.get(Properties.WATERLOGGED)) {
            world.scheduleFluidTick(
                pos,
                Fluids.WATER,
                Fluids.WATER.getTickRate(world),
            )
        }
        return connected(
            state = state,
            world = world,
            pos = pos,
        )
    }

    /** 玩家使用本格時只嘗試本格，不轉到鄰座或搶走乘客。 */
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
        if (world is ServerWorld) {
            seats.sit(
                world = world,
                pos = pos,
                rider = player,
                yaw = state.get(Properties.HORIZONTAL_FACING).asRotation(),
            )
        }
        return ActionResult.success(world.isClient)
    }

    /** 拆除本格立即釋放本格支撐；連接狀態更新不影響乘客。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onStateReplaced(
        state: BlockState,
        world: World,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (!state.isOf(newState.block) && !world.isClient) {
            ExampleChairSeatEntity.seatsAt(world, pos).forEach { seat ->
                seat.removeAllPassengers()
                seat.discard()
            }
        }
        super.onStateReplaced(
            state,
            world,
            pos,
            newState,
            moved,
        )
    }

    /** 含水長凳回報原生水源。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = if (state.get(Properties.WATERLOGGED)) Fluids.WATER.getStill(false) else Fluids.EMPTY.defaultState

    /** 結構旋轉保留局部左右語意，之後由鄰格檢查對齊連接。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState = state.with(Properties.HORIZONTAL_FACING, rotation.rotate(state.get(Properties.HORIZONTAL_FACING)))

    /** 鏡射同時交換左右端點。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState = if (mirror == BlockMirror.NONE) {
        state
    } else {
        state
            .with(Properties.HORIZONTAL_FACING, mirror.apply(state.get(Properties.HORIZONTAL_FACING)))
            .with(LEFT, state.get(RIGHT)).with(RIGHT, state.get(LEFT))
    }

    /** 連接狀態隨方塊保存，不保存整排清單。 */
    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(
            Properties.HORIZONTAL_FACING,
            Properties.WATERLOGGED,
            LEFT,
            RIGHT,
        )
    }

    /** 以兩個已載入的同高度鄰格產生連接狀態。 */
    private fun connected(
        state: BlockState,
        world: WorldAccess,
        pos: BlockPos,
    ): BlockState {
        val facing = state.get(Properties.HORIZONTAL_FACING)
        fun linked(side: Direction): Boolean {
            val neighbor = pos.offset(side)
            if (!world.isChunkLoaded(neighbor.x shr 4, neighbor.z shr 4)) return false
            val other = world.getBlockState(neighbor)
            return ExampleBenchConnections.connects(
                facing = facing,
                side = side,
                neighborFacing = if (other.isOf(this)) other.get(Properties.HORIZONTAL_FACING) else null,
            )
        }
        return state.with(LEFT, linked(facing.rotateYCounterclockwise())).with(RIGHT, linked(facing.rotateYClockwise()))
    }

    /** 讓身體中間保持可入座空間，扶手只佔未連接的兩端。 */
    private fun shape(state: BlockState): VoxelShape = ExampleBenchGeometry.boxes(
        facing = state.get(Properties.HORIZONTAL_FACING),
        leftConnected = state.get(LEFT),
        rightConnected = state.get(RIGHT),
    ).map { box ->
        createCuboidShape(
            box.minX,
            box.minY,
            box.minZ,
            box.maxX,
            box.maxY,
            box.maxZ,
        )
    }
        .reduce(VoxelShapes::union)

    /** 本格連接屬性及局部檢查頻率。 */
    companion object {
        /** 乘坐朝向的左側是否連接。 */
        val LEFT: BooleanProperty = BooleanProperty.of("left")

        /** 乘坐朝向的右側是否連接。 */
        val RIGHT: BooleanProperty = BooleanProperty.of("right")

        /** 每秒只查本格兩個鄰格，不查整排或載入區塊。 */
        private const val CONNECTION_CHECK_TICKS = 20
    }
}
