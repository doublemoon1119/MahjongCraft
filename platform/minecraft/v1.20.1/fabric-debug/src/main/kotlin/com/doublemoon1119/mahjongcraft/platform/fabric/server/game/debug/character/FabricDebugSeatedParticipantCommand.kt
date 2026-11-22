package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.character

import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.DebugMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.entity.EntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.SeatingSessionFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import kotlin.uuid.toKotlinUuid

/**
 * 為房主等待室建立一隻普通已入座護甲座，加入動作交給正式掃描，不直接改房間。
 *
 * @property persistence 目前等待室。
 * @property sessions 固定席位與正式座位協調器。
 * @property spawnGateway 正式實體生成入口。
 */
internal class FabricDebugSeatedParticipantCommand(
    private val persistence: AuthoritativeStatePersistence,
    private val sessions: SeatingSessionFactory,
    private val spawnGateway: EntitySpawnGateway,
) {
    /** 建立掛在既有 development-only、op 限定根節點下的最小驗證指令。 */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> = literal("seated_participant").executes { context ->
        val source = context.source
        val player = source.playerOrException
        val room = persistence.characterSnapshot()?.authoritative?.rooms?.values?.firstOrNull { it.hostId == player.uuid.toKotlinUuid() && !it.isFull }
        if (room == null || room.playerIds.size !in 1..3) {
            source.sendFailure(Component.translatable(DebugMessageKeys.SEATED_PARTICIPANT_UNAVAILABLE))
            return@executes 0
        }
        val seating = sessions.prepareTable(room.id) ?: return@executes 0
        if (seating.world !== player.level() ||
            TableInteractionReach.distance(player.x, player.y, player.z, seating.location.x, seating.location.y, seating.location.z) > TableInteractionReach.SURVIVAL
        ) {
            return@executes 0
        }
        for (request in seating.requests) {
            val actor = ArmorStand(seating.world, request.seat.x, request.seat.y, request.seat.z)
            actor.moveTo(request.seat.x, request.seat.y, request.seat.z, request.seat.yaw, 0f)
            actor.addTag(DebugSeatedParticipantAdapter.TAG)
            actor.addTag("${DebugSeatedParticipantAdapter.OWNER_TAG_PREFIX}${player.uuid.toKotlinUuid()}")
            if (!seating.canSeat(actor, request)) continue
            if (!spawnGateway.spawn(seating.world, actor, DebugSeatedParticipantAdapter.ID, room.id)) return@executes 0
            if (seating.assign(actor, request) != SeatAssignmentResult.SEATED) {
                actor.discard()
                source.sendFailure(Component.translatable(DebugMessageKeys.SEATED_PARTICIPANT_UNAVAILABLE))
                return@executes 0
            }
            source.sendSuccess({ Component.translatable(DebugMessageKeys.SEATED_PARTICIPANT_CREATED) }, false)
            return@executes 1
        }
        source.sendFailure(Component.translatable(DebugMessageKeys.SEATED_PARTICIPANT_UNAVAILABLE))
        0
    }
}
