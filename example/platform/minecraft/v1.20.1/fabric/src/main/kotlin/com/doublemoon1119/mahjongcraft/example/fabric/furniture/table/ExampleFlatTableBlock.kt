package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTableStructureBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTables
import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.Waterloggable
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.FluidState
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
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
 * 3×3 單層的範例麻將桌：中央是控制方塊，放置時一次擺好 9 格，任何一格被拆或被取代時整張桌子一起移除。
 *
 * 桌面在方塊頂端，上方 3×3 是空氣，正是 MahjongCraft 擺牌的那一層。外觀由 [design] 決定，結構與互動都相同。
 * 每一格與原版可含水方塊相同，各自依原位置是否為水源決定含水。
 *
 * @param settings Minecraft 方塊設定。
 * @property design 桌子的外觀款式。
 */
class ExampleFlatTableBlock(
    settings: Settings,
    private val design: ExampleFlatTableDesign,
) : BlockWithEntity(settings),
    MahjongTableStructureBlock,
    Waterloggable {
    /** 正在移除整張桌子的中央位置，避免各格的回呼重複清理。 */
    private val removingCenters = mutableSetOf<BlockPos>()

    /** 預設為朝北、不含水的中央格。 */
    init {
        defaultState = stateManager.defaultState
            .with(PART, ExampleFlatTablePart.CENTER)
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.WATERLOGGED, false)
    }

    /** 只有中央格建立方塊實體。 */
    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? = if (state.get(PART) == ExampleFlatTablePart.CENTER) ExampleTableBlockEntity(pos, state) else null

    /** 以方塊模型繪製。 */
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    /** 任何一格都回報中央的位置。 */
    override fun mahjongTableControllerPos(
        world: BlockView,
        pos: BlockPos,
        state: BlockState,
    ): BlockPos? = if (state.isOf(this)) ExampleFlatTableStructure.centerPos(pos, state.get(PART)) else null

    /** 右鍵任何一格都交給 MahjongCraft 開啟房間畫面。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult,
    ): ActionResult = MahjongTables.interact(world, pos, state, player)

    /** 9 個位置都可替換、而且沒有實體擋住時才允許放置；放在水源中的中央格含水。 */
    override fun getPlacementState(context: ItemPlacementContext): BlockState? {
        val centerPos = context.blockPos
        val facing = context.horizontalPlayerFacing.opposite
        val canPlace = ExampleFlatTableStructure.placements(centerPos).all { (part, partPos) ->
            val replaceable = part == ExampleFlatTablePart.CENTER || context.world.getBlockState(partPos).isReplaceable
            replaceable && context.world.canPlace(defaultState.with(PART, part), partPos, ShapeContext.absent())
        }
        if (!canPlace) return null
        return defaultState
            .with(Properties.HORIZONTAL_FACING, facing)
            .waterloggedAt(context.world, centerPos)
    }

    /** 中央格放置後擺好其餘 8 格，各自依原位置決定是否含水；中途失敗時移除這次擺上的格子。 */
    override fun onPlaced(
        world: World,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack,
    ) {
        super.onPlaced(world, pos, state, placer, itemStack)
        if (world.isClient || state.get(PART) != ExampleFlatTablePart.CENTER) return
        val placed = ExampleFlatTableStructure.placements(pos)
            .filterKeys { part -> part != ExampleFlatTablePart.CENTER }
            .all { (part, partPos) -> world.setBlockState(partPos, state.with(PART, part).waterloggedAt(world, partPos), NOTIFY_ALL) }
        if (!placed) world.removeBlock(pos, false)
    }

    /** 任何一格被換成其他方塊時，趁中央的方塊實體仍存在請 MahjongCraft 清理，再移除其餘格子。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onStateReplaced(
        state: BlockState,
        world: World,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (!world.isClient && !state.isOf(newState.block)) removeTable(world, pos, state)
        super.onStateReplaced(state, world, pos, newState, moved)
    }

    /**
     * 清理一次整張桌子並移除仍存在的其餘格子；被移除的格子不掉落物品，含水的格子留下水。
     *
     * @param world 桌子所在世界。
     * @param pos 被取代那一格的位置。
     * @param state 被取代那一格的方塊狀態。
     */
    private fun removeTable(world: World, pos: BlockPos, state: BlockState) {
        val centerPos = ExampleFlatTableStructure.centerPos(pos, state.get(PART)).toImmutable()
        if (!removingCenters.add(centerPos)) return
        try {
            MahjongTables.onControllerRemoved(world, centerPos)
            ExampleFlatTableStructure.placements(centerPos).values.forEach { partPos ->
                if (partPos != pos && world.getBlockState(partPos).isOf(this)) world.removeBlock(partPos, false)
            }
        } finally {
            removingCenters.remove(centerPos)
        }
    }

    /** 依格子位置回傳碰撞形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = design.shape(state.get(PART))

    /** 選取框與碰撞相同。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = design.shape(state.get(PART))

    /** 含水時回報水源的流體狀態。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = state.waterloggedFluidState()

    /** 含水時排程水流更新。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getStateForNeighborUpdate(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: WorldAccess,
        pos: BlockPos,
        neighborPos: BlockPos,
    ): BlockState {
        state.scheduleWaterTickIfWaterlogged(world, pos)
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos)
    }

    /** 宣告格子位置、朝向與含水狀態。 */
    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(PART, Properties.HORIZONTAL_FACING, Properties.WATERLOGGED)
    }

    /** 方塊狀態屬性。 */
    companion object {
        /** 這一格在 3×3 桌子中的位置。 */
        val PART: EnumProperty<ExampleFlatTablePart> = EnumProperty.of("part", ExampleFlatTablePart::class.java)
    }
}
