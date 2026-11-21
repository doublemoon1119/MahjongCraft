package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTableStructureBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTables
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 只占一格的範例麻將桌：方塊本身就是控制方塊，模型畫成 3×3 大小，碰撞與選取只占自己那一格。
 *
 * 桌面在方塊頂端，上方 3×3 是空氣，正是 MahjongCraft 擺牌的那一層。與原版可含水方塊相同，放在水源中會含水。
 *
 * @param settings Minecraft 方塊設定。
 */
class ExampleOneBlockTableBlock(
    settings: Properties,
) : BaseEntityBlock(settings),
    MahjongTableStructureBlock,
    SimpleWaterloggedBlock {
    /** 預設朝北、不含水。 */
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false),
        )
    }

    /** 每個方塊都是控制方塊，都建立方塊實體。 */
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ExampleTableBlockEntity(pos, state)

    /** 以方塊模型繪製。 */
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    /** 這張桌子只有一格，控制方塊就是自己。 */
    override fun mahjongTableControllerPos(
        world: BlockGetter,
        pos: BlockPos,
        state: BlockState,
    ): BlockPos? = pos.takeIf { state.`is`(this) }

    /** 右鍵交給 MahjongCraft 開啟房間畫面。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun use(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult = MahjongTables.interact(world, pos, state, player)

    /** 方塊被換成其他方塊時，趁方塊實體仍存在請 MahjongCraft 清理整局呈現。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRemove(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (!state.`is`(newState.block)) MahjongTables.onControllerRemoved(world, pos)
        super.onRemove(state, world, pos, newState, moved)
    }

    /** 放置時讓桌子朝向玩家，放在水源中時含水。 */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = defaultBlockState()
        .setValue(BlockStateProperties.HORIZONTAL_FACING, context.horizontalDirection.opposite)
        .waterloggedAt(context.level, context.clickedPos)

    /** 碰撞只占自己這一格：桌面板與中央桌腳。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

    /** 選取框與碰撞相同。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

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

    /** 宣告朝向與含水狀態。 */
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.WATERLOGGED)
    }

    /** 共用的形狀。 */
    private companion object {
        /** 桌面板與中央桌腳。 */
        val SHAPE: VoxelShape = Shapes.or(
            box(0.0, 13.0, 0.0, 16.0, 16.0, 16.0),
            box(6.0, 0.0, 6.0, 10.0, 13.0, 10.0),
        )
    }
}
