package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.SeatPlacementRegistry
import net.minecraft.world.InteractionResult
import java.util.UUID

/** 只攔截公開接管角色與已登記座位物品的右鍵，其餘實體保持原生互動。 */
internal object ClientAiSeatInteraction {
    /** 只在客戶端主執行緒存取的公開角色名單。 */
    private val targets = mutableSetOf<UUID>()

    /** 辨識物品失敗時記錄提供者，不讓回呼破壞輸入流程。 */
    private val logger = mahjongCraftLogger(ClientAiSeatInteraction::class)

    /**
     * 登記角色標記接收、斷線清除及右鍵路由。
     *
     * @param registry 已凍結的放置能力。
     * @param failures 第三方失敗的隔離政策。
     * @param json 共用線路格式。
     * @param events 登記用戶端事件用的事件來源。
     */
    fun register(
        registry: SeatPlacementRegistry,
        failures: ExtensionFailurePolicy,
        json: NetworkJson,
        events: ClientEvents,
    ) {
        MahjongChannels.aiCharacterTarget.registerClientReceiver(json) { payload ->
            val id = runCatching { UUID.fromString(payload.entityId) }.getOrNull() ?: return@registerClientReceiver
            if (payload.bound) targets += id else targets -= id
        }
        events.onClientDisconnect { targets.clear() }
        events.onClientEntityUnload { entity, _ -> targets -= entity.uuid }
        events.onClientUseEntity { player, _, hand, entity ->
            if (entity.uuid !in targets || player.isShiftKeyDown) return@onClientUseEntity InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (stack.isEmpty) return@onClientUseEntity InteractionResult.PASS
            for (definition in registry.entries()) {
                try {
                    if (definition.provider.supports(stack.copy())) return@onClientUseEntity InteractionResult.SUCCESS
                } catch (cause: Throwable) {
                    if (!failures.isIsolatable(cause)) throw cause
                    logger.warn("Seat item matching failed providerId={}", definition.id, cause)
                    return@onClientUseEntity InteractionResult.FAIL
                }
            }
            InteractionResult.PASS
        }
    }
}
