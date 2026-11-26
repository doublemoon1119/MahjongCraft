package com.doublemoon1119.mahjongcraft.example.minecraft.mod.character

import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.entity.ExampleSeatGeometry
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurnitureBlocks
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.seating.ExampleChairSeatService
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.seating.ExampleCushionSeatService
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.seating.ExampleFurnitureSpace
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.hasCustomName
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import kotlin.uuid.toKotlinUuid

/** 放置新的人偶，不替換已有參與者，也不修改 MahjongCraft 的房間。 */
internal object ExampleActorPlacement {
    /**
     * 在指定方塊建立人偶。命中範例方塊座位時立即走既有入座服務。
     *
     * @param world 建立世界。
     * @param player 使用物品的放置者。
     * @param stack 本次手持物品。
     * @param pos 放置位置或既有座位的方塊位置。
     * @return 建立及必要入座皆完成時為 true。
     */
    fun placeAtBlock(
        world: ServerLevel,
        player: Player,
        stack: ItemStack,
        pos: BlockPos,
    ): Boolean {
        if (!world.chunkSource.hasChunk(pos.x shr 4, pos.z shr 4)) return false
        val state = world.getBlockState(pos)
        val isSeat = ExampleFurnitureBlocks.isSeat(state)
        val yaw = if (state.`is`(ExampleFurnitureBlocks.bench)) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() else player.yRot
        val position = Vec3(
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
                        world = world,
                        pos = pos,
                        rider = actor,
                    )
                } else {
                    ExampleFurnitureSpace.isClear(
                        world = world,
                        actor = actor,
                        box = actor.boundingBox,
                    )
                }
            },
            mount = { actor ->
                !isSeat ||
                    seats.sit(
                        world = world,
                        pos = pos,
                        rider = actor,
                        yaw = yaw,
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
        world: ServerLevel,
        player: Player,
        stack: ItemStack,
        cushion: ExampleCushionEntity,
    ): Boolean = place(
        world = world,
        player = player,
        stack = stack,
        position = Vec3(
            cushion.x,
            ExampleSeatGeometry.cushionFeetY(cushion.y),
            cushion.z,
        ),
        yaw = cushion.yRot,
        canUse = { actor ->
            ExampleCushionSeatService.inspect(
                world = world,
                cushion = cushion,
                rider = actor,
            ) == SeatState.AVAILABLE
        },
        mount = { actor ->
            ExampleCushionSeatService.sit(
                world = world,
                cushion = cushion,
                rider = actor,
                yaw = cushion.yRot,
            )
        },
    )

    /** 權限、範圍、生成及耗材都在同一個同步呼叫內核對，入座失敗只刪除本次人偶。 */
    private fun place(
        world: ServerLevel,
        player: Player,
        stack: ItemStack,
        position: Vec3,
        yaw: Float,
        canUse: (ExampleActorEntity) -> Boolean,
        mount: (ExampleActorEntity) -> Boolean,
    ): Boolean {
        if (stack.isEmpty || !stack.`is`(ExampleActors.item) || player.level() !== world || player.isSpectator || !player.abilities.mayBuild) return false
        val actor = ExampleActors.actorType.create(world) ?: return false
        actor.ownerId = player.uuid.toKotlinUuid()
        actor.moveTo(
            position.x,
            position.y,
            position.z,
            yaw,
            0f,
        )
        actor.yHeadRot = yaw
        actor.yBodyRot = yaw
        if (stack.hasCustomName()) actor.customName = stack.hoverName.copy()
        val body = actor.boundingBox
        if (!ExampleFurnitureSpace.isLoaded(world, body)) return false
        val first = BlockPos.containing(
            body.minX,
            body.minY,
            body.minZ,
        )
        val last = BlockPos.containing(
            body.maxX,
            body.maxY,
            body.maxZ,
        )
        for (pos in BlockPos.betweenClosed(first, last)) {
            if (!world.mayInteract(player, pos) || !player.mayInteract(world, pos)) return false
        }
        if (!canUse(actor) || !world.addFreshEntity(actor)) return false
        var completed = false
        try {
            completed = actor.isAlive && mount(actor)
            if (completed && !player.abilities.instabuild) stack.shrink(1)
            return completed
        } finally {
            if (!completed) {
                actor.stopRiding()
                actor.discard()
            }
        }
    }
}
