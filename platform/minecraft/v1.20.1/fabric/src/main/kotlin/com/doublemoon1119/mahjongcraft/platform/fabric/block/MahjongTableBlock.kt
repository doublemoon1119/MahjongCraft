package com.doublemoon1119.mahjongcraft.platform.fabric.block

import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/** 第 4 階段使用的最小單方塊麻將桌；完整多方塊外觀與 3D 渲染留待後續里程碑。 */
class MahjongTableBlock(
    settings: Settings,
    private val roomService: MahjongTableRoomService,
    private val tableLifecycleService: FabricTableLifecycleService,
) : BlockWithEntity(settings) {
    /** 只為中央麻將桌方塊建立保存穩定 UUID 的方塊實體。 */
    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = MahjongTableBlockEntity(pos, state)

    /** 使用一般方塊模型顯示目前的最小佔位外觀。 */
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    /** 方塊被其他狀態替換時，保留最後位置並依 orphan policy 清理相關資料。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onStateReplaced(
        state: BlockState,
        world: World,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (state.block !== newState.block && world is ServerWorld) {
            val table = world.getBlockEntity(pos) as? MahjongTableBlockEntity
            if (table != null) tableLifecycleService.onBlockReplaced(world, table)
        }
        super.onStateReplaced(state, world, pos, newState, moved)
    }

    /** 在伺服器端把右鍵交給進場服務；蹲下右鍵則嘗試離開等待中的遊戲。 */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult,
    ): ActionResult {
        if (!world.isClient) {
            val table = world.getBlockEntity(pos) as? MahjongTableBlockEntity ?: return ActionResult.FAIL
            val serverPlayer = player as ServerPlayerEntity
            if (player.isSneaking) roomService.leave(table, serverPlayer) else roomService.interact(table, serverPlayer)
        }
        return ActionResult.SUCCESS
    }
}
