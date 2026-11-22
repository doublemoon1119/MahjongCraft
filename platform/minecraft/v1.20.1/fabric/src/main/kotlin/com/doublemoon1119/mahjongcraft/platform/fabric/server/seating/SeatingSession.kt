package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftSeatingKeys
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

/**
 * 一次入座使用的世界、席位與協調器。
 *
 * @property world 桌子所在世界。
 * @property requests 依座位 index 排列的固定席位。
 * @property coordinator 本次入座共用的協調器，跨玩家去重已認領的座位。
 * @property location 桌子的公開位置。
 * @property facing 桌子的公開朝向。
 */
class SeatingSession internal constructor(
    val world: ServerLevel,
    val requests: List<SeatRequest>,
    internal val coordinator: SeatAssignmentCoordinator<ServerLevel, Entity, VehicleSeatSnapshot>,
    val location: TableLocation,
    val facing: MahjongTableFacing,
) {
    /**
     * 試算這個實體能否坐上指定席位，不實際入座。
     *
     * @param rider 要入座的實體。
     * @param request 要坐的席位。
     * @return 找得到可坐的座位時為 true。
     */
    fun canSeat(rider: Entity, request: SeatRequest): Boolean = coordinator.previewSeat(world, rider, request) != null

    /**
     * 讓實體坐上指定席位。
     *
     * @param rider 要入座的實體。
     * @param request 要坐的席位。
     * @return 入座結果。
     */
    fun assign(rider: Entity, request: SeatRequest): SeatAssignmentResult = coordinator.assign(world, rider, request)

    /**
     * 入座不成功時提示玩家原因；成功入座或安全站立時不提示。
     *
     * @param player 入座的玩家。
     * @param result 入座結果。
     */
    fun notify(player: ServerPlayer, result: SeatAssignmentResult) {
        val key = when (result) {
            SeatAssignmentResult.SEATED, SeatAssignmentResult.STANDING -> null
            SeatAssignmentResult.OCCUPIED -> MinecraftSeatingKeys.OCCUPIED
            SeatAssignmentResult.BLOCKED -> MinecraftSeatingKeys.BLOCKED
            SeatAssignmentResult.RIDING -> MinecraftSeatingKeys.RIDING
            SeatAssignmentResult.FAILED -> MinecraftSeatingKeys.FAILED
        }
        if (key != null) player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), false)
    }
}
