package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementContext
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterBindings
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterBindingKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BlockUseInterception
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.C2SRateLimits
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.PlayerRequestRateLimiter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatItemPlacementCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatItemPlacementResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftSeatPlacementKeys
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
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
 * @property blockUse 載入器的方塊使用攔截，交給放置情境使用。
 */
internal class AiSeatPlacementService(
    private val bindings: AiCharacterBindings,
    private val persistence: AuthoritativeStatePersistence,
    private val sessions: SeatingSessionFactory,
    private val characters: AiCharacterManager,
    private val registry: SeatPlacementRegistry,
    private val failurePolicy: ExtensionFailurePolicy,
    private val blockUse: BlockUseInterception,
) {
    /** 依既有請求保護限制每位玩家的互動頻率。 */
    private val limiter = PlayerRequestRateLimiter(C2SRateLimits.seatPlacement)

    /** 防止相同接管在第三方回呼中重入。 */
    private val coordinator = SeatItemPlacementCoordinator<AiCharacterBindingKey>()

    /** 記錄提供者與接管，不輸出牌局私有資料。 */
    private val logger = mahjongCraftLogger(AiSeatPlacementService::class)

    /**
     * 在伺服器端處理原生實體互動；客戶端只負責輸入路由。
     *
     * @param events 登記事件用的伺服器事件來源。
     */
    fun registerEvents(events: ServerEvents) {
        events.onPlayerDisconnect { player -> limiter.forget(player.uuid.toKotlinUuid()) }
        events.onPlayerUseEntity(::interact)
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
        player: ServerPlayer,
        world: ServerLevel,
        hand: InteractionHand,
        entity: Entity,
    ): InteractionResult {
        check(world.server.isSameThread) { "Seat interaction requires the server thread" }
        if (player.isShiftKeyDown) return InteractionResult.PASS
        val (key, actor) = bindings.findEntity(entity) ?: return InteractionResult.PASS
        if (limiter.tryAcquire(player.uuid.toKotlinUuid()) != PlayerRequestRateLimiter.Result.Accepted) return InteractionResult.FAIL
        if (entity.isPassenger || entity.isVehicle) return InteractionResult.FAIL
        val stack = player.getItemInHand(hand).copy()
        if (stack.isEmpty) return InteractionResult.PASS
        var providerId: String? = null
        try {
            val definition = registry.entries().firstOrNull {
                providerId = it.id
                it.provider.supports(stack.copy())
            } ?: return InteractionResult.PASS
            providerId = definition.id
            val game = persistence.characterSnapshot()?.authoritative?.games?.get(key.tableId) ?: return InteractionResult.FAIL
            val roster = game.tableState.players.map { it.id }
            val index = roster.indexOf(key.playerId)
            if (index < 0) return InteractionResult.FAIL
            val seating = sessions.prepare(key.tableId, roster) ?: return InteractionResult.FAIL
            val context = FabricSeatPlacementContext.create(
                world = world,
                player = player,
                hand = hand,
                rider = entity,
                request = seating.requests[index],
                blockUse = blockUse,
            )
            val result = coordinator.attempt(
                key = key,
                valid = {
                    player.isAlive &&
                        !player.isSpectator &&
                        player.level() === world &&
                        seating.world === world &&
                        player.distanceToSqr(entity) <= MAXIMUM_DISTANCE_SQUARED &&
                        player.distanceToSqr(context.request.seat.x, context.request.seat.y, context.request.seat.z) <= MAXIMUM_DISTANCE_SQUARED &&
                        entity.distanceToSqr(context.request.seat.x, context.request.seat.y, context.request.seat.z) <= MAXIMUM_DISTANCE_SQUARED &&
                        !entity.isPassenger &&
                        !entity.isVehicle &&
                        persistence.characterSnapshot()?.authoritative?.games?.get(key.tableId) == game &&
                        bindings.find(key.tableId, key.playerId) === actor &&
                        context.canModify(AABB(context.blockPos))
                },
                place = {
                    if (!ItemStack.matches(stack, player.getItemInHand(hand)) ||
                        !context.isSpaceClear(AABB(context.blockPos)) ||
                        !seating.coordinator.isVacant(world, entity, context.request)
                    ) {
                        false
                    } else {
                        val checked = entity.blocksBuilding
                        try {
                            entity.blocksBuilding = false
                            definition.provider.place(context)
                        } finally {
                            entity.blocksBuilding = checked
                        }
                    }
                },
                seat = { characters.seatAfterPlacement(key, actor) },
            )
            when (result) {
                SeatItemPlacementResult.SEATED -> return InteractionResult.SUCCESS
                SeatItemPlacementResult.PLACED_UNSEATED -> notify(player, MinecraftSeatPlacementKeys.PLACED_UNSEATED)
                SeatItemPlacementResult.BUSY -> return InteractionResult.FAIL
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
            return InteractionResult.FAIL
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
            return InteractionResult.FAIL
        }
    }

    /**
     * 提示互動結果，不把放置成功但未入座當作物品放置失敗。
     *
     * @param player 發起者。
     * @param key 翻譯鍵。
     */
    private fun notify(player: ServerPlayer, key: String) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), false)
    }

    /** 與原生實體互動相同的最遠距離。 */
    private companion object {
        /** 六格的平方距離。 */
        const val MAXIMUM_DISTANCE_SQUARED: Double = 36.0
    }
}
