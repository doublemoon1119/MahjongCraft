package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.character

import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatingSessionFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import kotlin.uuid.toKotlinUuid

/**
 * 為房主等待室建立一隻普通已入座護甲座，加入動作交給正式掃描，不直接改房間。
 *
 * @property persistence 目前等待室。
 * @property sessions 固定席位與正式座位協調器。
 * @property spawnGateway 正式實體生成入口。
 */
internal class FabricDebugSeatedParticipantCommand(
    private val persistence: FabricAuthoritativeStatePersistence,
    private val sessions: FabricSeatingSessionFactory,
    private val spawnGateway: FabricEntitySpawnGateway,
) {
    /** 建立掛在既有 development-only、op 限定根節點下的最小驗證指令。 */
    fun build(): LiteralArgumentBuilder<ServerCommandSource> = literal("seated_participant").executes { context ->
        val source = context.source
        val player = source.playerOrThrow
        val room = persistence.characterSnapshot()?.authoritative?.rooms?.values?.firstOrNull { it.hostId == player.uuid.toKotlinUuid() && !it.isFull }
        if (room == null || room.playerIds.size !in 1..3) {
            source.sendError(Text.translatable(MinecraftMessageKeys.DEBUG_SEATED_PARTICIPANT_UNAVAILABLE))
            return@executes 0
        }
        val seating = sessions.prepareTable(room.id) ?: return@executes 0
        if (seating.world !== player.world ||
            TableInteractionReach.distance(player.x, player.y, player.z, seating.location.x, seating.location.y, seating.location.z) > TableInteractionReach.SURVIVAL
        ) {
            return@executes 0
        }
        for (request in seating.requests) {
            val actor = ArmorStandEntity(seating.world, request.seat.x, request.seat.y, request.seat.z)
            actor.refreshPositionAndAngles(request.seat.x, request.seat.y, request.seat.z, request.seat.yaw, 0f)
            actor.addCommandTag(DebugSeatedParticipantAdapter.TAG)
            actor.addCommandTag("${DebugSeatedParticipantAdapter.OWNER_TAG_PREFIX}${player.uuid.toKotlinUuid()}")
            if (seating.coordinator.previewSeat(seating.world, actor, request) == null) continue
            if (!spawnGateway.spawn(seating.world, actor, DebugSeatedParticipantAdapter.ID, room.id)) return@executes 0
            if (seating.coordinator.assign(seating.world, actor, request) != SeatAssignmentResult.SEATED) {
                actor.discard()
                source.sendError(Text.translatable(MinecraftMessageKeys.DEBUG_SEATED_PARTICIPANT_UNAVAILABLE))
                return@executes 0
            }
            source.sendFeedback({ Text.translatable(MinecraftMessageKeys.DEBUG_SEATED_PARTICIPANT_CREATED) }, false)
            return@executes 1
        }
        source.sendError(Text.translatable(MinecraftMessageKeys.DEBUG_SEATED_PARTICIPANT_UNAVAILABLE))
        0
    }
}
