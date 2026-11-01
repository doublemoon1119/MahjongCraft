package com.doublemoon1119.mahjongcraft.platform.fabric.api.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatCollisionPolicy
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

/**
 * 一次伺服器座位放置的資料，只由 MahjongCraft 建立。
 *
 * @property world 已載入的目標世界。
 * @property player 真正執行互動的玩家。
 * @property hand 觸發互動的手；耗材只來自這隻手。
 * @property rider 即將入座的角色，不得改變其身分或移往其他位置。
 * @property request MahjongCraft 決定的固定席位。
 */
class FabricSeatPlacementContext private constructor(
    val world: ServerWorld,
    val player: ServerPlayerEntity,
    val hand: Hand,
    val rider: Entity,
    val request: SeatRequest,
) {
    /** 固定席位腳底所在的方塊位置。 */
    val blockPos: BlockPos
        get() = BlockPos.ofFloored(request.seat.x, request.seat.y, request.seat.z)

    /**
     * 檢查完整範圍的區塊、邊界與玩家改動權限，不載入區塊。
     *
     * @param box 放置物件會占用的範圍。
     * @return 範圍可查詢且玩家獲准改動時為 true；仍須由提供者檢查碰撞與支撐。
     */
    fun canModify(box: Box): Boolean {
        check(world.server.isOnThread) { "Seat placement requires the server thread" }
        if (player.isSpectator || !player.abilities.allowModifyWorld || player.world !== world) return false
        if (box.minY < world.bottomY || box.maxY >= world.topY || !world.worldBorder.contains(box)) return false
        val minimum = BlockPos.ofFloored(box.minX - 1.0, box.minY - 1.0, box.minZ - 1.0)
        val maximum = BlockPos.ofFloored(box.maxX + 1.0, box.maxY, box.maxZ + 1.0)
        for (x in (minimum.x shr 4)..(maximum.x shr 4)) {
            for (z in (minimum.z shr 4)..(maximum.z shr 4)) {
                if (!world.chunkManager.isChunkLoaded(x, z)) return false
            }
        }
        val first = BlockPos.ofFloored(box.minX, box.minY - 1.0, box.minZ)
        val last = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ)
        for (pos in BlockPos.iterate(first, last)) {
            if (!world.canPlayerModifyAt(player, pos) || !player.canModifyAt(world, pos)) return false
        }
        return true
    }

    /**
     * 檢查放置範圍的方塊與身體碰撞，不把純顯示面板當作占位物件。
     *
     * @param box 完整放置範圍。
     * @return 已載入、獲准改動且沒有阻擋時為 true。
     */
    fun isSpaceClear(box: Box): Boolean = canModify(box) &&
        world.isSpaceEmpty(rider, box) &&
        world.getOtherEntities(rider, box) {
            SeatCollisionPolicy.blocks(
                removed = it.isRemoved,
                spectator = it.isSpectator,
                physical = it is LivingEntity || it.isCollidable,
            )
        }.isEmpty()

    /**
     * 通知原生方塊使用攔截器，保留其他模組拒絕放置的機會。
     *
     * @return 攔截器允許繼續放置時為 true。
     */
    fun allowsBlockUse(): Boolean {
        val stack = player.getStackInHand(hand)
        val before = stack.copy()
        val result = UseBlockCallback.EVENT.invoker().interact(
            player,
            world,
            hand,
            supportHit(),
        )
        return result == ActionResult.PASS &&
            rider.isAlive &&
            rider.world === world &&
            canModify(Box(blockPos)) &&
            player.getStackInHand(hand) === stack &&
            ItemStack.areEqual(before, stack)
    }

    /**
     * 以真正玩家及手持 BlockItem 的放置流程放在席位上；物品自行扣除及觸發原生放置回呼。
     *
     * @param placementYaw 傳給物品的放置視角；物品作者須依自己方塊的 facing 語意換算。
     * @return 成功放置時為 true；入座由 MahjongCraft 隨後安排。
     */
    fun placeBlockItem(placementYaw: Float): Boolean {
        val stack = player.getStackInHand(hand)
        val item = stack.item as? BlockItem ?: return false
        if (stack.isEmpty || !canModify(Box(blockPos)) || !allowsBlockUse()) return false
        if (player.getStackInHand(hand) !== stack || stack.isEmpty) return false
        if (!world.getBlockState(blockPos.down()).isSideSolidFullSquare(world, blockPos.down(), Direction.UP)) return false
        val context = object : ItemPlacementContext(
            world,
            player,
            hand,
            stack,
            supportHit(),
        ) {
            /** 使用提供者選擇的放置朝向，不改動玩家視角。 */
            override fun getPlayerYaw(): Float = placementYaw

            /** 使用相同的水平朝向建立方塊狀態。 */
            override fun getHorizontalPlayerFacing(): Direction = Direction.fromRotation(placementYaw.toDouble())
        }
        if (context.blockPos != blockPos) return false
        return item.place(context).isAccepted
    }

    /**
     * 建立席位正下方的頂面命中，不選取鄰座或其他方塊。
     *
     * @return 放置物品使用的支撐面命中。
     */
    private fun supportHit(): BlockHitResult = BlockHitResult(
        Vec3d(blockPos.x + 0.5, blockPos.y.toDouble(), blockPos.z + 0.5),
        Direction.UP,
        blockPos.down(),
        false,
    )

    /** 內部建立當次互動，不提供第三方自行偽造請求。 */
    internal companion object {
        /**
         * 建立主執行緒的放置情境。
         *
         * @param world 目標世界。
         * @param player 互動玩家。
         * @param hand 互動手。
         * @param rider 即將入座的角色。
         * @param request 固定席位。
         * @return 本次回呼資料。
         */
        @JvmSynthetic
        fun create(
            world: ServerWorld,
            player: ServerPlayerEntity,
            hand: Hand,
            rider: Entity,
            request: SeatRequest,
        ): FabricSeatPlacementContext = FabricSeatPlacementContext(
            world = world,
            player = player,
            hand = hand,
            rider = rider,
            request = request,
        )
    }
}
