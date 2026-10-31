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
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.FluidState
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World
import net.minecraft.world.WorldAccess

/**
 * 只占一格的範例麻將桌：方塊本身就是控制方塊，模型畫成 3×3 大小，碰撞與選取只占自己那一格。
 *
 * 桌面在方塊頂端，上方 3×3 是空氣，正是 MahjongCraft 擺牌的那一層。與原版可含水方塊相同，放在水源中會含水。
 *
 * @param settings Minecraft 方塊設定。
 */
class ExampleOneBlockTableBlock(
    settings: Settings,
) : BlockWithEntity(settings),
    MahjongTableStructureBlock,
    Waterloggable {
    /** 預設朝北、不含水。 */
    init {
        defaultState = stateManager.defaultState
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
            .with(Properties.WATERLOGGED, false)
    }

    /** 每個方塊都是控制方塊，都建立方塊實體。 */
    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ExampleTableBlockEntity(pos, state)

    /** 以方塊模型繪製。 */
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    /** 這張桌子只有一格，控制方塊就是自己。 */
    override fun mahjongTableControllerPos(
        world: BlockView,
        pos: BlockPos,
        state: BlockState,
    ): BlockPos? = pos.takeIf { state.isOf(this) }

    /** 右鍵交給 MahjongCraft 開啟房間畫面。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult,
    ): ActionResult = MahjongTables.interact(world, pos, state, player)

    /** 方塊被換成其他方塊時，趁方塊實體仍存在請 MahjongCraft 清理整局呈現。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onStateReplaced(
        state: BlockState,
        world: World,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (!state.isOf(newState.block)) MahjongTables.onControllerRemoved(world, pos)
        super.onStateReplaced(state, world, pos, newState, moved)
    }

    /** 放置時讓桌子朝向玩家，放在水源中時含水。 */
    override fun getPlacementState(context: ItemPlacementContext): BlockState = defaultState
        .with(Properties.HORIZONTAL_FACING, context.horizontalPlayerFacing.opposite)
        .waterloggedAt(context.world, context.blockPos)

    /** 碰撞只占自己這一格：桌面板與中央桌腳。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = SHAPE

    /** 選取框與碰撞相同。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = SHAPE

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

    /** 宣告朝向與含水狀態。 */
    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.HORIZONTAL_FACING, Properties.WATERLOGGED)
    }

    /** 共用的形狀。 */
    private companion object {
        /** 桌面板與中央桌腳。 */
        val SHAPE: VoxelShape = VoxelShapes.union(
            createCuboidShape(0.0, 13.0, 0.0, 16.0, 16.0, 16.0),
            createCuboidShape(6.0, 0.0, 6.0, 10.0, 13.0, 10.0),
        )
    }
}
