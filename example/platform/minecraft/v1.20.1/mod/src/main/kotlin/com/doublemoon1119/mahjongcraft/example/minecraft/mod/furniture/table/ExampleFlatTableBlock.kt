package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table.ExampleFlatTableDesign
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table.ExampleFlatTablePart
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table.ExampleFlatTableStructure
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table.scheduleWaterTickIfWaterlogged
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table.waterloggedAt
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.table.waterloggedFluidState
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.ExampleEntityBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.table.MahjongTableStructureBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.table.MahjongTables
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

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
    settings: Properties,
    private val design: ExampleFlatTableDesign,
) : ExampleEntityBlock(settings),
    MahjongTableStructureBlock,
    SimpleWaterloggedBlock {
    /** 正在移除整張桌子的中央位置，避免各格的回呼重複清理。 */
    private val removingCenters = mutableSetOf<BlockPos>()

    /** 預設為朝北、不含水的中央格。 */
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(PART, ExampleFlatTablePart.CENTER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false),
        )
    }

    /** 只有中央格建立方塊實體。 */
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? = if (state.getValue(PART) == ExampleFlatTablePart.CENTER) ExampleTableBlockEntity(pos, state) else null

    /** 以方塊模型繪製。 */
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    /** 任何一格都回報中央的位置。 */
    override fun mahjongTableControllerPos(
        world: BlockGetter,
        pos: BlockPos,
        state: BlockState,
    ): BlockPos? = if (state.`is`(this)) ExampleFlatTableStructure.centerPos(pos, state.getValue(PART)) else null

    /** 右鍵任何一格都交給 MahjongCraft 開啟房間畫面。 */
    override fun useWithoutItem(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult = MahjongTables.interact(world, pos, state, player)

    /** 9 個位置都可替換、而且沒有實體擋住時才允許放置；放在水源中的中央格含水。 */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val centerPos = context.clickedPos
        val facing = context.horizontalDirection.opposite
        val canPlace = ExampleFlatTableStructure.placements(centerPos).all { (part, partPos) ->
            val replaceable = part == ExampleFlatTablePart.CENTER || context.level.getBlockState(partPos).canBeReplaced()
            replaceable && context.level.isUnobstructed(defaultBlockState().setValue(PART, part), partPos, CollisionContext.empty())
        }
        if (!canPlace) return null
        return defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
            .waterloggedAt(context.level, centerPos)
    }

    /** 中央格放置後擺好其餘 8 格，各自依原位置決定是否含水；中途失敗時移除這次擺上的格子。 */
    override fun setPlacedBy(
        world: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack,
    ) {
        super.setPlacedBy(world, pos, state, placer, itemStack)
        if (world.isClientSide || state.getValue(PART) != ExampleFlatTablePart.CENTER) return
        val placed = ExampleFlatTableStructure.placements(pos)
            .filterKeys { part -> part != ExampleFlatTablePart.CENTER }
            .all { (part, partPos) -> world.setBlock(partPos, state.setValue(PART, part).waterloggedAt(world, partPos), UPDATE_ALL) }
        if (!placed) world.removeBlock(pos, false)
    }

    /** 任何一格被換成其他方塊時，趁中央的方塊實體仍存在請 MahjongCraft 清理，再移除其餘格子。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRemove(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (!world.isClientSide && !state.`is`(newState.block)) removeTable(world, pos, state)
        super.onRemove(state, world, pos, newState, moved)
    }

    /**
     * 清理一次整張桌子並移除仍存在的其餘格子；被移除的格子不掉落物品，含水的格子留下水。
     *
     * @param world 桌子所在世界。
     * @param pos 被取代那一格的位置。
     * @param state 被取代那一格的方塊狀態。
     */
    private fun removeTable(world: Level, pos: BlockPos, state: BlockState) {
        val centerPos = ExampleFlatTableStructure.centerPos(pos, state.getValue(PART)).immutable()
        if (!removingCenters.add(centerPos)) return
        try {
            MahjongTables.onControllerRemoved(world, centerPos)
            ExampleFlatTableStructure.placements(centerPos).values.forEach { partPos ->
                if (partPos != pos && world.getBlockState(partPos).`is`(this)) world.removeBlock(partPos, false)
            }
        } finally {
            removingCenters.remove(centerPos)
        }
    }

    /** 依格子位置回傳碰撞形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = design.shape(state.getValue(PART))

    /** 選取框與碰撞相同。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = design.shape(state.getValue(PART))

    /** 含水時回報水源的流體狀態。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getFluidState(state: BlockState): FluidState = state.waterloggedFluidState()

    /** 含水時排程水流更新。 */
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

    /** 宣告格子位置、朝向與含水狀態。 */
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(PART, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.WATERLOGGED)
    }

    /** 方塊狀態屬性。 */
    companion object {
        /** 這一格在 3×3 桌子中的位置。 */
        val PART: EnumProperty<ExampleFlatTablePart> = EnumProperty.create("part", ExampleFlatTablePart::class.java)
    }
}
