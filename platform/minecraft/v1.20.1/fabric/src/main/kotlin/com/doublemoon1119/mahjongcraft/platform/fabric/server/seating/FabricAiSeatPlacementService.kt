package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.network.C2SRateLimits
import com.doublemoon1119.mahjongcraft.platform.fabric.network.PlayerRequestRateLimiter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricAiCharacterBindings
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricAiCharacterManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterBindingKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatItemPlacementCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatItemPlacementResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftSeatPlacementKeys
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.math.Box
import kotlin.uuid.toKotlinUuid

/**
 * 驗證玩家右鍵的本場 AI，放置成功後沿用固定席位入座。
 *
 * @property bindings 角色接管索引。
 * @property persistence 目前權威資料。
 * @property sessions 固定席位解析。
 * @property characters 入座與角色回饋。
 * @property registry 已凍結的物品放置能力。
 * @property failurePolicy 第三方失敗的隔離政策。
 */
internal class FabricAiSeatPlacementService(
    private val bindings: FabricAiCharacterBindings,
    private val persistence: FabricAuthoritativeStatePersistence,
    private val sessions: FabricSeatingSessionFactory,
    private val characters: FabricAiCharacterManager,
    private val registry: FabricSeatPlacementRegistry,
    private val failurePolicy: ExtensionFailurePolicy,
) {
    /** 依既有請求保護限制每位玩家的互動頻率。 */
    private val limiter = PlayerRequestRateLimiter(C2SRateLimits.seatPlacement)

    /** 防止相同接管在第三方回呼中重入。 */
    private val coordinator = SeatItemPlacementCoordinator<AiCharacterBindingKey>()

    /** 記錄提供者與接管，不輸出牌局私有資料。 */
    private val logger = mahjongCraftLogger(FabricAiSeatPlacementService::class)

    /** 在伺服器端處理原生實體互動；客戶端只負責輸入路由。 */
    fun registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> limiter.forget(handler.player.uuid.toKotlinUuid()) }
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (world !is ServerWorld || player !is ServerPlayerEntity) ActionResult.PASS else interact(player, world, hand, entity)
        }
    }

    /**
     * 由真正點擊的實體與手持物品發起一次放置，不接受客戶端指定席位。
     *
     * @param player 發起者。
     * @param world 目前世界。
     * @param hand 觸發手。
     * @param entity 被點擊的原生角色。
     * @return 是否處理這次互動。
     */
    private fun interact(
        player: ServerPlayerEntity,
        world: ServerWorld,
        hand: Hand,
        entity: Entity,
    ): ActionResult {
        check(world.server.isOnThread) { "Seat interaction requires the server thread" }
        if (player.isSneaking) return ActionResult.PASS
        val (key, actor) = bindings.findEntity(entity) ?: return ActionResult.PASS
        if (limiter.tryAcquire(player.uuid.toKotlinUuid()) != PlayerRequestRateLimiter.Result.Accepted) return ActionResult.FAIL
        if (entity.hasVehicle() || entity.hasPassengers()) return ActionResult.FAIL
        val stack = player.getStackInHand(hand).copy()
        if (stack.isEmpty) return ActionResult.PASS
        var providerId: String? = null
        try {
            val definition = registry.entries().firstOrNull {
                providerId = it.id
                it.provider.supports(stack.copy())
            } ?: return ActionResult.PASS
            providerId = definition.id
            val game = persistence.characterSnapshot()?.authoritative?.games?.get(key.tableId) ?: return ActionResult.FAIL
            val roster = game.tableState.players.map { it.id }
            val index = roster.indexOf(key.playerId)
            if (index < 0) return ActionResult.FAIL
            val seating = sessions.prepare(key.tableId, roster) ?: return ActionResult.FAIL
            val context = FabricSeatPlacementContext.create(
                world = world,
                player = player,
                hand = hand,
                rider = entity,
                request = seating.requests[index],
            )
            val result = coordinator.attempt(
                key = key,
                valid = {
                    player.isAlive &&
                        !player.isSpectator &&
                        player.world === world &&
                        seating.world === world &&
                        player.squaredDistanceTo(entity) <= MAXIMUM_DISTANCE_SQUARED &&
                        player.squaredDistanceTo(context.request.seat.x, context.request.seat.y, context.request.seat.z) <= MAXIMUM_DISTANCE_SQUARED &&
                        entity.squaredDistanceTo(context.request.seat.x, context.request.seat.y, context.request.seat.z) <= MAXIMUM_DISTANCE_SQUARED &&
                        !entity.hasVehicle() &&
                        !entity.hasPassengers() &&
                        persistence.characterSnapshot()?.authoritative?.games?.get(key.tableId) == game &&
                        bindings.find(key.tableId, key.playerId) === actor &&
                        context.canModify(Box(context.blockPos))
                },
                place = {
                    if (!ItemStack.areEqual(stack, player.getStackInHand(hand)) ||
                        !context.isSpaceClear(Box(context.blockPos)) ||
                        !seating.coordinator.isVacant(world, entity, context.request)
                    ) {
                        false
                    } else {
                        val checked = entity.intersectionChecked
                        try {
                            entity.intersectionChecked = false
                            definition.provider.place(context)
                        } finally {
                            entity.intersectionChecked = checked
                        }
                    }
                },
                seat = { characters.seatAfterPlacement(key, actor) },
            )
            when (result) {
                SeatItemPlacementResult.SEATED -> return ActionResult.SUCCESS
                SeatItemPlacementResult.PLACED_UNSEATED -> notify(player, MinecraftSeatPlacementKeys.PLACED_UNSEATED)
                SeatItemPlacementResult.BUSY -> return ActionResult.FAIL
                SeatItemPlacementResult.REJECTED, SeatItemPlacementResult.NOT_PLACED -> {
                    logger.debug(
                        "AI seat placement rejected providerId={} tableId={} seatIndex={} result={}",
                        providerId,
                        key.tableId,
                        index,
                        result,
                    )
                    notify(player, MinecraftSeatPlacementKeys.NOT_PLACED)
                }
            }
            return ActionResult.FAIL
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            logger.warn(
                "AI seat placement failed providerId={} tableId={} matchId={} playerId={}",
                providerId,
                key.tableId,
                key.matchId,
                key.playerId,
                cause,
            )
            notify(player, MinecraftSeatPlacementKeys.FAILED)
            return ActionResult.FAIL
        }
    }

    /**
     * 提示互動結果，不把放置成功但未入座當作物品放置失敗。
     *
     * @param player 發起者。
     * @param key 翻譯鍵。
     */
    private fun notify(player: ServerPlayerEntity, key: String) {
        player.sendMessage(Text.translatable(key).formatted(Formatting.YELLOW), false)
    }

    /** 與原生實體互動相同的最遠距離。 */
    private companion object {
        /** 六格的平方距離。 */
        const val MAXIMUM_DISTANCE_SQUARED: Double = 36.0
    }
}
