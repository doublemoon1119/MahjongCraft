package com.doublemoon1119.mahjongcraft.example.fabric.furniture.block

import com.doublemoon1119.mahjongcraft.example.fabric.character.ExampleActors
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleChairSeatService
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
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
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 每格一席、沿左右延長的範例長凳，不建立整排控制器。
 *
 * @param settings 原生方塊設定。
 */
class ExampleBenchBlock(settings: Properties) :
    Block(settings),
    SimpleWaterloggedBlock {
    /** 真人與角色共用的家具入座服務。 */
    private val seats = ExampleChairSeatService()

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false)
                .setValue(LEFT, false)
                .setValue(RIGHT, false),
        )
    }

    /** 每格的椅面、靠背與端點扶手，共用模型相同的朝向。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = shape(state)

    /** 選取範圍與實際家具一致。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = shape(state)

    /** 開口面向放置者，座位物品能力另外換算所需放置視角。 */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = connected(
        state = defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, ExampleBenchPlacement.facing(context.horizontalDirection))
            .setValue(BlockStateProperties.WATERLOGGED, context.level.getFluidState(context.clickedPos).type == Fluids.WATER),
        world = context.level,
        pos = context.clickedPos,
    )

    /** 排程固定範圍的鄰格檢查；原生排程會隨區塊保存及恢復。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPlace(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        oldState: BlockState,
        notify: Boolean,
    ) {
        if (world is ServerLevel) {
            world.scheduleTick(
                pos,
                this,
                CONNECTION_CHECK_TICKS,
            )
        }
    }

    /** 只重查本格左右兩格，使區塊邊界重新可用時恢復正確連接。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun tick(
        state: BlockState,
        world: ServerLevel,
        pos: BlockPos,
        random: RandomSource,
    ) {
        val next = connected(
            state = state,
            world = world,
            pos = pos,
        )
        if (next != state) {
            world.setBlock(
                pos,
                next,
                UPDATE_ALL,
            )
        }
        world.scheduleTick(
            pos,
            this,
            CONNECTION_CHECK_TICKS,
        )
    }

    /** 鄰格變動時立即重算局部連接，未載入的格子不查詢。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos,
    ): BlockState {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            world.scheduleTick(
                pos,
                Fluids.WATER,
                Fluids.WATER.getTickDelay(world),
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
    override fun use(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        if (player.isShiftKeyDown || player.isPassenger || player.getItemInHand(hand).`is`(ExampleActors.item)) return InteractionResult.PASS
        if (world is ServerLevel) {
            seats.sit(
                world = world,
                pos = pos,
                rider = player,
                yaw = state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot(),
            )
        }
        return InteractionResult.sidedSuccess(world.isClientSide)
    }

    /** 拆除本格立即釋放本格支撐；連接狀態更新不影響乘客。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRemove(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (!state.`is`(newState.block) && !world.isClientSide) {
            ExampleChairSeatEntity.seatsAt(world, pos).forEach { seat ->
                seat.ejectPassengers()
                seat.discard()
            }
        }
        super.onRemove(
            state,
            world,
            pos,
            newState,
            moved,
        )
    }

    /** 含水長凳回報原生水源。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = if (state.getValue(BlockStateProperties.WATERLOGGED)) Fluids.WATER.getSource(false) else Fluids.EMPTY.defaultFluidState()

    /** 結構旋轉保留局部左右語意，之後由鄰格檢查對齊連接。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))

    /** 鏡射同時交換左右端點。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun mirror(state: BlockState, mirror: Mirror): BlockState = if (mirror == Mirror.NONE) {
        state
    } else {
        state
            .setValue(BlockStateProperties.HORIZONTAL_FACING, mirror.mirror(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))
            .setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT))
    }

    /** 連接狀態隨方塊保存，不保存整排清單。 */
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(
            BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.WATERLOGGED,
            LEFT,
            RIGHT,
        )
    }

    /** 以兩個已載入的同高度鄰格產生連接狀態。 */
    private fun connected(
        state: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
    ): BlockState {
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        fun linked(side: Direction): Boolean {
            val neighbor = pos.relative(side)
            if (!world.hasChunk(neighbor.x shr 4, neighbor.z shr 4)) return false
            val other = world.getBlockState(neighbor)
            return ExampleBenchConnections.connects(
                facing = facing,
                side = side,
                neighborFacing = if (other.`is`(this)) other.getValue(BlockStateProperties.HORIZONTAL_FACING) else null,
            )
        }
        return state.setValue(LEFT, linked(facing.counterClockWise)).setValue(RIGHT, linked(facing.clockWise))
    }

    /** 讓身體中間保持可入座空間，扶手只佔未連接的兩端。 */
    private fun shape(state: BlockState): VoxelShape = ExampleBenchGeometry.boxes(
        facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING),
        leftConnected = state.getValue(LEFT),
        rightConnected = state.getValue(RIGHT),
    ).map { box ->
        box(
            box.minX,
            box.minY,
            box.minZ,
            box.maxX,
            box.maxY,
            box.maxZ,
        )
    }
        .reduce(Shapes::or)

    /** 本格連接屬性及局部檢查頻率。 */
    companion object {
        /** 乘坐朝向的左側是否連接。 */
        val LEFT: BooleanProperty = BooleanProperty.create("left")

        /** 乘坐朝向的右側是否連接。 */
        val RIGHT: BooleanProperty = BooleanProperty.create("right")

        /** 每秒只查本格兩個鄰格，不查整排或載入區塊。 */
        private const val CONNECTION_CHECK_TICKS = 20
    }
}
