package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftSeatingKeys
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 一次入座使用的世界、席位與協調器。
 *
 * @property world 桌子所在世界。
 * @property requests 依座位 index 排列的固定席位。
 * @property coordinator 本次入座共用的協調器，跨玩家去重已認領的座位。
 * @property location 桌子的公開位置。
 * @property facing 桌子的公開朝向。
 */
internal class FabricSeatingSession(
    val world: ServerWorld,
    val requests: List<SeatRequest>,
    val coordinator: SeatAssignmentCoordinator<ServerWorld, Entity, FabricSeatSnapshot>,
    val location: TableLocation,
    val facing: MahjongTableFacing,
) {
    /**
     * 入座不成功時提示玩家原因；成功入座或安全站立時不提示。
     *
     * @param player 入座的玩家。
     * @param result 入座結果。
     */
    fun notify(player: ServerPlayerEntity, result: SeatAssignmentResult) {
        val key = when (result) {
            SeatAssignmentResult.SEATED, SeatAssignmentResult.STANDING -> null
            SeatAssignmentResult.OCCUPIED -> MinecraftSeatingKeys.OCCUPIED
            SeatAssignmentResult.BLOCKED -> MinecraftSeatingKeys.BLOCKED
            SeatAssignmentResult.RIDING -> MinecraftSeatingKeys.RIDING
            SeatAssignmentResult.FAILED -> MinecraftSeatingKeys.FAILED
        }
        if (key != null) player.sendMessage(Text.translatable(key).formatted(Formatting.YELLOW), false)
    }
}
