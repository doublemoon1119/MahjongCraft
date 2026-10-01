package com.doublemoon1119.mahjongcraft.platform.fabric.block

import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatService
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * 使用 [design] 固定款式、水平朝向及其碰撞幾何的單方塊麻將凳。
 *
 * 右鍵即可坐下，不論手上拿著什麼；蹲下或正在騎乘時不坐下。凳子本身不保存牌局或座位狀態。
 *
 * @property design 此方塊 registry ID 對應的固定凳子款式。
 * @property seatService 讓玩家坐上凳子的伺服器端入口。
 */
class MahjongStoolBlock(
    settings: Settings,
    val design: MahjongStoolDesign,
    private val seatService: MahjongStoolSeatService,
) : Block(settings) {
    init {
        defaultState = stateManager.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH)
    }

    /** 放置時讓凳子的正面開口朝向玩家。 */
    override fun getPlacementState(context: ItemPlacementContext): BlockState = defaultState.with(
        Properties.HORIZONTAL_FACING,
        context.horizontalPlayerFacing.opposite,
    )

    /**
     * 讓右鍵的玩家坐上凳子。
     *
     * 蹲下或正在騎乘時交回原版處理；其他情況一律消耗這次右鍵，即使凳子已被占用而沒有坐下，手上的物品也不會被使用。
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult,
    ): ActionResult {
        if (player.isSneaking || player.hasVehicle()) return ActionResult.PASS
        if (world is ServerWorld) seatService.sit(world, pos, player)
        return ActionResult.success(world.isClient)
    }

    /** 回傳依凳子方向旋轉的碰撞形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = design.shapeFor(state.get(Properties.HORIZONTAL_FACING))

    /** 回傳與碰撞相同並依凳子方向旋轉的選取形狀。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = design.shapeFor(state.get(Properties.HORIZONTAL_FACING))

    /** 套用結構方塊等操作要求的水平旋轉。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState = state.with(
        Properties.HORIZONTAL_FACING,
        rotation.rotate(state.get(Properties.HORIZONTAL_FACING)),
    )

    /** 套用結構方塊等操作要求的水平鏡像。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState = state.rotate(
        mirror.getRotation(state.get(Properties.HORIZONTAL_FACING)),
    )

    /** 宣告所有麻將凳皆保存水平朝向。 */
    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.HORIZONTAL_FACING)
    }
}
