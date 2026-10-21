package com.doublemoon1119.mahjongcraft.example.fabric.furniture.block

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleChairSeatService
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * 可直接右鍵坐下的範例方塊椅子。
 *
 * @param settings Minecraft 方塊設定。
 */
class ExampleChairBlock(
    settings: Settings,
) : Block(settings) {
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
        if (player.isSneaking || player.hasVehicle()) return ActionResult.PASS
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
}
