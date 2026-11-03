package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleSeatGeometry
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleChairSeatService
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleCushionSeatService
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSpace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.uuid.toKotlinUuid

/** 放置新的人偶，不替換已有參與者，也不修改 MahjongCraft 的房間。 */
internal object ExampleActorPlacement {
    /**
     * 在指定方塊建立人偶；命中範例方塊座位時立即走既有入座服務。
     *
     * @param world 建立世界。
     * @param player 使用物品的放置者。
     * @param stack 本次手持物品。
     * @param pos 放置位置或既有座位的方塊位置。
     * @return 建立及必要入座皆完成時為 true。
     */
    fun placeAtBlock(
        world: ServerWorld,
        player: PlayerEntity,
        stack: ItemStack,
        pos: BlockPos,
    ): Boolean {
        if (!world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)) return false
        val state = world.getBlockState(pos)
        val isSeat = ExampleFurnitureBlocks.isSeat(state)
        val yaw = if (state.isOf(ExampleFurnitureBlocks.bench)) state.get(Properties.HORIZONTAL_FACING).asRotation() else player.yaw
        val position = Vec3d(
            pos.x + 0.5,
            if (isSeat) ExampleSeatGeometry.passengerFeetY(ExampleSeatGeometry.chairSeatY(pos.y)) else pos.y.toDouble(),
            pos.z + 0.5,
        )
        val seats = ExampleChairSeatService()
        return place(
            world = world,
            player = player,
            stack = stack,
            position = position,
            yaw = yaw,
            canUse = { actor ->
                if (isSeat) {
                    seats.canSit(
                        world,
                        pos,
                        actor,
                    )
                } else {
                    ExampleFurnitureSpace.isClear(
                        world,
                        actor,
                        actor.boundingBox,
                    )
                }
            },
            mount = { actor ->
                !isSeat ||
                    seats.sit(
                        world,
                        pos,
                        actor,
                        yaw,
                    )
            },
        )
    }

    /**
     * 在實際坐墊位置建立並入座，不移動坐墊或原乘客。
     *
     * @param world 建立世界。
     * @param player 使用物品的放置者。
     * @param stack 本次手持物品。
     * @param cushion 命中的已載入坐墊。
     * @return 建立並入座成功時為 true。
     */
    fun placeOnCushion(
        world: ServerWorld,
        player: PlayerEntity,
        stack: ItemStack,
        cushion: ExampleCushionEntity,
    ): Boolean = place(
        world = world,
        player = player,
        stack = stack,
        position = Vec3d(
            cushion.x,
            ExampleSeatGeometry.cushionFeetY(cushion.y),
            cushion.z,
        ),
        yaw = cushion.yaw,
        canUse = { actor ->
            ExampleCushionSeatService.inspect(
                world,
                cushion,
                actor,
            ) == SeatState.AVAILABLE
        },
        mount = { actor ->
            ExampleCushionSeatService.sit(
                world,
                cushion,
                actor,
                cushion.yaw,
            )
        },
    )

    /** 權限、範圍、生成及耗材都在同一個同步呼叫內核對，入座失敗只刪除本次人偶。 */
    private fun place(
        world: ServerWorld,
        player: PlayerEntity,
        stack: ItemStack,
        position: Vec3d,
        yaw: Float,
        canUse: (ExampleActorEntity) -> Boolean,
        mount: (ExampleActorEntity) -> Boolean,
    ): Boolean {
        if (stack.isEmpty || !stack.isOf(ExampleActors.item) || player.world !== world || player.isSpectator || !player.abilities.allowModifyWorld) return false
        val actor = ExampleActors.actorType.create(world) ?: return false
        actor.ownerId = player.uuid.toKotlinUuid()
        actor.refreshPositionAndAngles(
            position.x,
            position.y,
            position.z,
            yaw,
            0f,
        )
        actor.headYaw = yaw
        actor.bodyYaw = yaw
        if (stack.hasCustomName()) actor.customName = stack.name.copy()
        val body = actor.boundingBox
        if (!ExampleFurnitureSpace.isLoaded(world, body)) return false
        val first = BlockPos.ofFloored(
            body.minX,
            body.minY,
            body.minZ,
        )
        val last = BlockPos.ofFloored(
            body.maxX,
            body.maxY,
            body.maxZ,
        )
        for (pos in BlockPos.iterate(first, last)) {
            if (!world.canPlayerModifyAt(player, pos) || !player.canModifyAt(world, pos)) return false
        }
        if (!canUse(actor) || !world.spawnEntity(actor)) return false
        var completed = false
        try {
            completed = actor.isAlive && mount(actor)
            if (completed && !player.abilities.creativeMode) stack.decrement(1)
            return completed
        } finally {
            if (!completed) {
                actor.stopRiding()
                actor.discard()
            }
        }
    }
}
