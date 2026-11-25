package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.MahjongTableRoomService
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 建立 `/mahjongcraft debug room start <seat>`：由房主開局，但指定自己的座位。
 *
 * - 其他成員依房間原本的順序坐其餘座位。
 * - 開局走與房間畫面相同的正式流程，包括開局前的檢查、入座與之後的推進。
 * - 只把隨機排座換成指定的順序，讓換座位相關的情況可以固定重現。
 *
 * @property persistence 查詢呼叫者所在的等待房間。
 * @property roomService 正式的開局入口。
 */
internal class DebugRoomCommand(
    private val persistence: AuthoritativeStatePersistence,
    private val roomService: MahjongTableRoomService,
) {
    /**
     * 建立指定座位開局的開發指令樹。
     *
     * @return `room` 指令節點。
     */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> = literal(ROOM_SUBCOMMAND).then(
        literal(START_SUBCOMMAND).then(
            argument(SEAT_ARGUMENT, IntegerArgumentType.integer(0))
                .executes { context -> start(context.source, IntegerArgumentType.getInteger(context, SEAT_ARGUMENT)) },
        ),
    )

    /**
     * 以指定座位要求開局。
     *
     * - 房主、準備狀態等檢查由正式開局流程負責。
     * - 結果以一般的開局回饋通知。
     *
     * @param source 指令發起來源。
     * @param seat 呼叫者要坐的座位，以開場座位順序的位置表示。
     * @return 已送出開局要求時為 1，否則為 0。
     */
    private fun start(source: CommandSourceStack, seat: Int): Int {
        val player = source.player
        if (player == null) {
            source.sendFailure(Component.literal("This debug subcommand must be run by a player"))
            return 0
        }
        val playerId = player.uuid.toKotlinUuid()
        val room = persistence.characterSnapshot()?.authoritative?.rooms?.values?.firstOrNull { playerId in it.playerIds }
        if (room == null) {
            source.sendFailure(Component.literal("You are not in a mahjong room"))
            return 0
        }
        if (seat >= room.playerIds.size) {
            source.sendFailure(Component.literal("Seat $seat does not exist in a room of ${room.playerIds.size} players"))
            return 0
        }
        roomService.start(player = player, seatOrder = seatOrder(room.playerIds, playerId, seat))
        source.sendSuccess({ Component.literal("Requested a match start with you at seat $seat") }, true)
        return 1
    }

    /** 指定座位開局的節點名稱。 */
    companion object {
        /** 房間操作的根 literal。 */
        const val ROOM_SUBCOMMAND: String = "room"

        /** 指定座位開局的 literal。 */
        const val START_SUBCOMMAND: String = "start"

        /** 座位參數名稱。 */
        private const val SEAT_ARGUMENT: String = "seat"

        /**
         * 把指定玩家放到指定座位，其他成員保留原本的相對順序。
         *
         * @param playerIds 房間成員原本的順序。
         * @param playerId 要指定座位的玩家。
         * @param seat 指定的座位位置。
         * @return 開場的座位順序。
         */
        internal fun seatOrder(playerIds: List<Uuid>, playerId: Uuid, seat: Int): List<Uuid> {
            require(playerId in playerIds && seat in playerIds.indices) { "The player and seat must belong to the room" }
            return (playerIds - playerId).toMutableList().apply { add(seat, playerId) }
        }
    }
}
