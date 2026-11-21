package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.SeatCollisionScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.coroutines.cancellation.CancellationException

/** 在生成前預檢失敗時記錄附近空間，只讀原生狀態，不再次呼叫座位提供者。 */
internal object FabricAiCharacterActivationDiagnostics {
    /** 診斷只寫入 DEBUG 日誌，不送給玩家。 */
    private val logger = mahjongCraftLogger(FabricAiCharacterActivationDiagnostics::class)

    /**
     * 記錄指定位置附近的方塊、實體、站立碰撞及腳下支撐；未載入區塊時只記錄範圍。
     *
     * @param world 預檢使用的伺服器世界。
     * @param rider 尚未加入世界的角色。
     * @param request 原預檢使用的指定席位。
     * @param seatIndex 完整名冊中的席位索引。
     */
    fun beforeSpawn(
        world: ServerLevel,
        rider: Entity,
        request: SeatRequest,
        seatIndex: Int,
    ) {
        if (!logger.isDebugEnabled) return
        try {
            val seat = request.seat
            val dimensionsValid = rider.bbWidth.isFinite() && rider.bbHeight.isFinite() && rider.bbWidth > 0f && rider.bbHeight > 0f
            val probe = AABB(
                seat.x - PROBE_RADIUS,
                seat.y - PROBE_RADIUS,
                seat.z - PROBE_RADIUS,
                seat.x + PROBE_RADIUS,
                seat.y + PROBE_HEIGHT,
                seat.z + PROBE_RADIUS,
            )
            val minimum = BlockPos.containing(
                probe.minX - 1.0,
                probe.minY,
                probe.minZ - 1.0,
            )
            val maximum = BlockPos.containing(
                probe.maxX + 1.0,
                probe.maxY,
                probe.maxZ + 1.0,
            )
            val probeLoaded = minimum.y >= world.minBuildHeight &&
                maximum.y < world.maxBuildHeight &&
                ((minimum.x shr 4)..(maximum.x shr 4)).all { x ->
                    ((minimum.z shr 4)..(maximum.z shr 4)).all { z -> world.chunkSource.hasChunk(x, z) }
                }
            logger.debug(
                "AI character activation position tableId={} seatIndex={} entityId={} dimension={} target={} dimensions={}x{} dimensionsValid={} probe={} probeLoaded={}",
                request.tableId,
                seatIndex,
                rider.uuid,
                request.dimensionId,
                seat,
                rider.bbWidth,
                rider.bbHeight,
                dimensionsValid,
                probe,
                probeLoaded,
            )
            if (!probeLoaded || !dimensionsValid) return
            val halfWidth = minOf(rider.bbWidth.toDouble() / 2.0, PROBE_RADIUS)
            val body = AABB(
                seat.x - halfWidth,
                seat.y,
                seat.z - halfWidth,
                seat.x + halfWidth,
                seat.y + minOf(rider.bbHeight.toDouble(), PROBE_HEIGHT),
                seat.z + halfWidth,
            )
            val support = AABB(
                body.minX,
                body.minY - SUPPORT_DEPTH,
                body.minZ,
                body.maxX,
                body.minY,
                body.maxZ,
            )
            val entities = world.getEntities(rider, probe)
            val samples = entities.take(MAX_SAMPLES).map { other ->
                "type=${BuiltInRegistries.ENTITY_TYPE.getKey(other.type)} uuid=${other.uuid} pos=${other.position()} box=${other.boundingBox} standingOverlap=${other.boundingBox.intersects(body)} invisible=${other.isInvisible} living=${other is LivingEntity} collidable=${other.canBeCollidedWith()} removed=${other.isRemoved} spectator=${other.isSpectator} ignoredBySeatMove=${SeatCollisionScope.ignores(rider, other)} passengerCount=${other.passengers.size} passengers=${other.passengers.take(MAX_SAMPLES).map { it.uuid }}"
            }
            val blocks = BlockPos.betweenClosed(
                BlockPos.containing(
                    probe.minX,
                    probe.minY,
                    probe.minZ,
                ),
                BlockPos.containing(
                    probe.maxX,
                    probe.maxY,
                    probe.maxZ,
                ),
            )
                .asSequence().mapNotNull { pos ->
                    val state = world.getBlockState(pos)
                    if (state.isAir) null else "${pos.toShortString()}:${BuiltInRegistries.BLOCK.getKey(state.block)}"
                }.take(MAX_SAMPLES).toList()
            val collisions = world.getBlockCollisions(rider, body).asSequence().take(MAX_SAMPLES).map { it.bounds() }.toList()
            val supported = world.getBlockCollisions(rider, support).iterator().hasNext()
            logger.debug(
                "AI character activation observations tableId={} seatIndex={} bodyProbe={} bodyProbeClipped={} insideBorder={} blockCollisions={} support={} targetBlock={} fluid={} nearbyEntityCount={} entitySamples={} blockSamples={}",
                request.tableId,
                seatIndex,
                body,
                rider.bbWidth / 2.0 > PROBE_RADIUS || rider.bbHeight > PROBE_HEIGHT,
                world.worldBorder.isWithinBounds(body),
                collisions,
                supported,
                BuiltInRegistries.BLOCK.getKey(
                    world.getBlockState(
                        BlockPos.containing(
                            seat.x,
                            seat.y,
                            seat.z,
                        ),
                    ).block,
                ),
                BuiltInRegistries.FLUID.getKey(
                    world.getFluidState(
                        BlockPos.containing(
                            seat.x,
                            seat.y,
                            seat.z,
                        ),
                    ).type,
                ),
                entities.size,
                samples,
                blocks,
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            logger.debug("AI character activation diagnostics failed tableId=${request.tableId} seatIndex=$seatIndex", cause)
        } catch (cause: LinkageError) {
            logger.debug("AI character activation diagnostics failed tableId=${request.tableId} seatIndex=$seatIndex", cause)
        }
    }

    /** 只查指定位置水平方向一格內的範圍。 */
    private const val PROBE_RADIUS = 1.0

    /** 查詢最高三格，較大的角色會標示取樣已裁切。 */
    private const val PROBE_HEIGHT = 3.0

    /** 每種日誌最多列出八筆，避免附近大量實體或方塊塞滿日誌。 */
    private const val MAX_SAMPLES = 8

    /** 站立腳底下十六分之一格的支撐查詢。 */
    private const val SUPPORT_DEPTH = 1.0 / 16.0
}
